package me.zohar.lottery.platform.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.alicp.jetcache.anno.Cached;
import com.zengtengpeng.annotation.Lock;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.common.vo.PageResult;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.dictconfig.ConfigHolder;
import me.zohar.lottery.gatheringcode.domain.GatheringCode;
import me.zohar.lottery.gatheringcode.repo.GatheringCodeRepo;
import me.zohar.lottery.mastercontrol.domain.PlatformOrderSetting;
import me.zohar.lottery.mastercontrol.repo.PlatformOrderSettingRepo;
import me.zohar.lottery.platform.domain.Platform;
import me.zohar.lottery.platform.domain.PlatformOrder;
import me.zohar.lottery.platform.domain.ReceiveOrderSituation;
import me.zohar.lottery.platform.domain.TodayReceiveOrderSituation;
import me.zohar.lottery.platform.param.MyReceiveOrderRecordQueryCondParam;
import me.zohar.lottery.platform.param.PlatformOrderQueryCondParam;
import me.zohar.lottery.platform.param.StartOrderParam;
import me.zohar.lottery.platform.repo.PlatformOrderRepo;
import me.zohar.lottery.platform.repo.PlatformRepo;
import me.zohar.lottery.platform.repo.ReceiveOrderSituationRepo;
import me.zohar.lottery.platform.repo.TodayReceiveOrderSituationRepo;
import me.zohar.lottery.platform.vo.BountyRankVO;
import me.zohar.lottery.platform.vo.MyReceiveOrderRecordVO;
import me.zohar.lottery.platform.vo.MyWaitConfirmOrderVO;
import me.zohar.lottery.platform.vo.MyWaitReceivingOrderVO;
import me.zohar.lottery.platform.vo.OrderGatheringCodeVO;
import me.zohar.lottery.platform.vo.PlatformOrderVO;
import me.zohar.lottery.useraccount.domain.AccountChangeLog;
import me.zohar.lottery.useraccount.domain.UserAccount;
import me.zohar.lottery.useraccount.repo.AccountChangeLogRepo;
import me.zohar.lottery.useraccount.repo.UserAccountRepo;

@Validated
@Slf4j
@Service
public class PlatformOrderService {

	@Autowired
	private PlatformOrderRepo platformOrderRepo;

	@Autowired
	private PlatformRepo platformRepo;

	@Autowired
	private UserAccountRepo userAccountRepo;

	@Autowired
	private GatheringCodeRepo gatheringCodeRepo;

	@Autowired
	private AccountChangeLogRepo accountChangeLogRepo;

	@Autowired
	private TodayReceiveOrderSituationRepo todayReceiveOrderSituationRepo;

	@Autowired
	private ReceiveOrderSituationRepo receiveOrderSituationRepo;

	@Autowired
	private PlatformOrderSettingRepo platformOrderSettingRepo;

	@Transactional
	public void platformConfirmToPaid(@NotBlank String secretKey, @NotBlank String orderId) {
		Platform platform = platformRepo.findBySecretKey(secretKey);
		if (platform == null) {
			throw new BizException(BizError.平台未接入);
		}
		PlatformOrder order = platformOrderRepo.findById(orderId).orElse(null);
		if (order == null) {
			log.error("平台订单不存在;secretKey:{},orderId:{}", secretKey, orderId);
			throw new BizException(BizError.平台订单不存在);
		}
		if (!order.getPlatformId().equals(platform.getId())) {
			log.error("无权更新平台订单状态为平台已确认支付;secretKey:{},orderId:{}", secretKey, orderId);
			throw new BizException(BizError.无权更新平台订单状态为平台已确认支付);
		}
		if (!Constant.平台订单状态_已接单.equals(order.getOrderState())) {
			throw new BizException(BizError.订单状态为已接单才能转为平台已确认支付);
		}
		order.platformConfirmToPaid();
		platformOrderRepo.save(order);
	}

	@Transactional(readOnly = true)
	public OrderGatheringCodeVO getOrderGatheringCode(@NotBlank String secretKey, @NotBlank String orderId) {
		Platform platform = platformRepo.findBySecretKey(secretKey);
		if (platform == null) {
			throw new BizException(BizError.平台未接入);
		}
		PlatformOrder order = platformOrderRepo.findById(orderId).orElse(null);
		if (order == null) {
			log.error("平台订单不存在;secretKey:{},orderId:{}", secretKey, orderId);
			throw new BizException(BizError.平台订单不存在);
		}
		if (!order.getPlatformId().equals(platform.getId())) {
			log.error("无权获取平台订单收款码信息;secretKey:{},orderId:{}", secretKey, orderId);
			throw new BizException(BizError.无权获取平台订单收款码信息);
		}

		OrderGatheringCodeVO vo = OrderGatheringCodeVO.convertFor(order);
		if (Constant.平台订单状态_已接单.equals(vo.getOrderState())) {
			GatheringCode gatheringCode = gatheringCodeRepo
					.findByUserAccountIdAndGatheringChannelCodeAndGatheringAmount(order.getReceivedAccountId(),
							order.getGatheringChannelCode(), order.getGatheringAmount());
			if (gatheringCode != null) {
				String gatheringCodeUrl = ConfigHolder.getConfigValue("storageUrl") + gatheringCode.getStorageId();
				vo.setGatheringCodeUrl(gatheringCodeUrl);
			}
		}
		return vo;
	}

	@Transactional
	public void confirmToPaid(@NotBlank String userAccountId, @NotBlank String orderId) {
		PlatformOrder platformOrder = platformOrderRepo.findById(orderId).orElse(null);
		if (platformOrder == null) {
			throw new BizException(BizError.平台订单不存在);
		}
		if (!(Constant.平台订单状态_已接单.equals(platformOrder.getOrderState())
				|| Constant.平台订单状态_平台已确认支付.equals(platformOrder.getOrderState()))) {
			throw new BizException(BizError.订单状态为已接单或平台已确认支付才能转为确认已支付);
		}
		if (!platformOrder.getReceivedAccountId().equals(userAccountId)) {
			throw new BizException(BizError.无权确认订单);
		}
		UserAccount userAccount = userAccountRepo.findById(userAccountId).get();
		Double cashDeposit = NumberUtil.round(userAccount.getCashDeposit() - platformOrder.getGatheringAmount(), 4)
				.doubleValue();
		if (cashDeposit < 0) {
			throw new BizException(BizError.保证金不足无法转为确认已支付);
		}
		userAccount.setCashDeposit(cashDeposit);
		userAccountRepo.save(userAccount);

		platformOrder.confirmToPaid();
		platformOrderRepo.save(platformOrder);
		accountChangeLogRepo.save(AccountChangeLog.buildWithConfirmToPaid(userAccount, platformOrder));
		receiveOrderBountySettlement(platformOrder, userAccount);
	}

	/**
	 * 接单奖励金结算
	 */
	@Transactional
	public void receiveOrderBountySettlement(PlatformOrder platformOrder, UserAccount userAccount) {
		PlatformOrderSetting setting = platformOrderSettingRepo.findTopByOrderByLatelyUpdateTime();
		if (setting == null || !setting.getReturnWaterRateEnabled() || setting.getReturnWaterRate() == null) {
			platformOrder.updateBounty(0d);
			platformOrderRepo.save(platformOrder);
			return;
		}

		double returnWater = platformOrder.getGatheringAmount() * setting.getReturnWaterRate() * 0.01;
		double cashDeposit = userAccount.getCashDeposit() + returnWater;

		platformOrder.updateBounty(returnWater);
		platformOrderRepo.save(platformOrder);
		userAccount.setCashDeposit(NumberUtil.round(cashDeposit, 4).doubleValue());
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(
				AccountChangeLog.buildWithReceiveOrderBounty(userAccount, returnWater, setting.getReturnWaterRate()));
	}

	@Transactional(readOnly = true)
	public List<MyWaitConfirmOrderVO> findMyWaitConfirmOrder(@NotBlank String userAccountId) {
		return MyWaitConfirmOrderVO
				.convertFor(platformOrderRepo.findByOrderStateInAndReceivedAccountIdOrderBySubmitTimeDesc(
						Arrays.asList(Constant.平台订单状态_已接单, Constant.平台订单状态_平台已确认支付), userAccountId));
	}

	@Transactional(readOnly = true)
	public List<MyWaitReceivingOrderVO> findMyWaitReceivingOrder(@NotBlank String userAccountId) {
		UserAccount userAccount = userAccountRepo.getOne(userAccountId);
		Double waitConfirmOrderAmount = 0d;
		List<PlatformOrder> waitConfirmOrders = platformOrderRepo
				.findByOrderStateInAndReceivedAccountIdOrderBySubmitTimeDesc(
						Arrays.asList(Constant.平台订单状态_已接单, Constant.平台订单状态_平台已确认支付), userAccountId);
		for (PlatformOrder waitConfirmOrder : waitConfirmOrders) {
			waitConfirmOrderAmount += waitConfirmOrder.getGatheringAmount();
		}
		Double surplusCashDeposit = NumberUtil.round(userAccount.getCashDeposit() - waitConfirmOrderAmount, 4)
				.doubleValue();
		List<PlatformOrder> waitReceivingOrders = platformOrderRepo
				.findByOrderStateAndGatheringAmountIsLessThanEqualOrderBySubmitTimeDesc(Constant.平台订单状态_等待接单,
						surplusCashDeposit);
		return MyWaitReceivingOrderVO.convertFor(waitReceivingOrders);
	}

	@ParamValid
	@Transactional
	public PlatformOrderVO startOrder(StartOrderParam param) {
		Platform platform = platformRepo.findBySecretKey(param.getSecretKey());
		if (platform == null) {
			throw new BizException(BizError.平台未接入);
		}

		Integer orderEffectiveDuration = Constant.平台订单默认有效时长;
		PlatformOrderSetting setting = platformOrderSettingRepo.findTopByOrderByLatelyUpdateTime();
		if (setting != null) {
			orderEffectiveDuration = setting.getOrderEffectiveDuration();
		}

		PlatformOrder platformOrder = param.convertToPo(platform.getId(), orderEffectiveDuration);
		platformOrderRepo.save(platformOrder);
		return PlatformOrderVO.convertFor(platformOrder);
	}

	/**
	 * 接单
	 * 
	 * @param param
	 * @return
	 */
	@Lock(keys = "'receiveOrder_' + #orderId")
	@Transactional
	public void receiveOrder(@NotBlank String userAccountId, @NotBlank String orderId) {
		PlatformOrder platformOrder = platformOrderRepo.getOne(orderId);
		if (platformOrder == null) {
			throw new BizException(BizError.平台订单不存在);
		}
		if (!Constant.平台订单状态_等待接单.equals(platformOrder.getOrderState())) {
			throw new BizException(BizError.订单已被接或已取消);
		}
		UserAccount userAccount = userAccountRepo.getOne(userAccountId);
		Double waitConfirmOrderAmount = 0d;
		List<PlatformOrder> waitConfirmOrders = platformOrderRepo
				.findByOrderStateInAndReceivedAccountIdOrderBySubmitTimeDesc(
						Arrays.asList(Constant.平台订单状态_已接单, Constant.平台订单状态_平台已确认支付), userAccountId);
		for (PlatformOrder waitConfirmOrder : waitConfirmOrders) {
			waitConfirmOrderAmount += waitConfirmOrder.getGatheringAmount();
		}
		Double surplusCashDeposit = NumberUtil.round(userAccount.getCashDeposit() - waitConfirmOrderAmount, 4)
				.doubleValue();
		if (surplusCashDeposit < platformOrder.getGatheringAmount()) {
			throw new BizException(BizError.保证金不足无法接单);
		}
		platformOrder.updateReceived(userAccount.getId());
		platformOrderRepo.save(platformOrder);
	}

	@ParamValid
	@Transactional(readOnly = true)
	public PageResult<MyReceiveOrderRecordVO> findMyReceiveOrderRecordByPage(MyReceiveOrderRecordQueryCondParam param) {
		Specification<PlatformOrder> spec = new Specification<PlatformOrder>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<PlatformOrder> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (param.getReceiveOrderTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("receivedTime").as(Date.class),
							DateUtil.beginOfDay(param.getReceiveOrderTime())));
				}
				if (param.getReceiveOrderTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("receivedTime").as(Date.class),
							DateUtil.endOfDay(param.getReceiveOrderTime())));
				}
				if (StrUtil.isNotEmpty(param.getGatheringChannelCode())) {
					predicates.add(builder.equal(root.get("gatheringChannelCode"), param.getGatheringChannelCode()));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<PlatformOrder> result = platformOrderRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("receivedTime"))));
		PageResult<MyReceiveOrderRecordVO> pageResult = new PageResult<>(
				MyReceiveOrderRecordVO.convertFor(result.getContent()), param.getPageNum(), param.getPageSize(),
				result.getTotalElements());
		return pageResult;
	}

	@Cached(name = "todayTop10BountyRank", expire = 300)
	@Transactional(readOnly = true)
	public List<BountyRankVO> findTodayTop10BountyRank() {
		List<TodayReceiveOrderSituation> todayReceiveOrderSituations = todayReceiveOrderSituationRepo
				.findTop10ByOrderByTotalBountyDesc();
		return BountyRankVO.convertForToday(todayReceiveOrderSituations);
	}

	@Cached(name = "top10BountyRank", expire = 300)
	@Transactional(readOnly = true)
	public List<BountyRankVO> findTop10BountyRank() {
		List<ReceiveOrderSituation> receiveOrderSituations = receiveOrderSituationRepo
				.findTop10ByOrderByTotalBountyDesc();
		return BountyRankVO.convertFor(receiveOrderSituations);
	}

	@Transactional(readOnly = true)
	public PageResult<PlatformOrderVO> findPlatformOrderByPage(PlatformOrderQueryCondParam param) {
		Specification<PlatformOrder> spec = new Specification<PlatformOrder>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<PlatformOrder> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (StrUtil.isNotBlank(param.getOrderNo())) {
					predicates.add(builder.equal(root.get("orderNo"), param.getOrderNo()));
				}

				if (StrUtil.isNotBlank(param.getPlatformName())) {
					predicates.add(
							builder.equal(root.join("platform", JoinType.INNER).get("name"), param.getPlatformName()));
				}
				if (StrUtil.isNotBlank(param.getGatheringChannelCode())) {
					predicates.add(builder.equal(root.get("gatheringChannelCode"), param.getGatheringChannelCode()));
				}
				if (StrUtil.isNotBlank(param.getOrderState())) {
					predicates.add(builder.equal(root.get("orderState"), param.getOrderState()));
				}
				if (StrUtil.isNotBlank(param.getReceiverUserName())) {
					predicates.add(builder.equal(root.join("userAccount", JoinType.INNER).get("userName"),
							param.getReceiverUserName()));
				}
				if (param.getSubmitStartTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("submitTime").as(Date.class),
							DateUtil.beginOfDay(param.getSubmitStartTime())));
				}
				if (param.getSubmitEndTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("submitTime").as(Date.class),
							DateUtil.endOfDay(param.getSubmitEndTime())));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<PlatformOrder> result = platformOrderRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("submitTime"))));
		PageResult<PlatformOrderVO> pageResult = new PageResult<>(PlatformOrderVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

	/**
	 * 取消订单
	 * 
	 * @param id
	 */
	@Transactional
	public void cancelOrder(@NotBlank String id) {
		PlatformOrder platformOrder = platformOrderRepo.getOne(id);
		if (!Constant.平台订单状态_等待接单.equals(platformOrder.getOrderState())) {
			throw new BizException(BizError.只有等待接单状态的平台订单才能取消);
		}
		platformOrder.setOrderState(Constant.平台订单状态_人工取消);
		platformOrder.setDealTime(new Date());
		platformOrderRepo.save(platformOrder);
	}

}
