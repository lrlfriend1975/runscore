package me.zohar.lottery.rechargewithdraw.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.common.vo.PageResult;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.rechargewithdraw.domain.WithdrawRecord;
import me.zohar.lottery.rechargewithdraw.param.StartWithdrawParam;
import me.zohar.lottery.rechargewithdraw.param.WithdrawRecordQueryCondParam;
import me.zohar.lottery.rechargewithdraw.repo.WithdrawRecordRepo;
import me.zohar.lottery.rechargewithdraw.vo.WithdrawRecordVO;
import me.zohar.lottery.useraccount.domain.AccountChangeLog;
import me.zohar.lottery.useraccount.domain.UserAccount;
import me.zohar.lottery.useraccount.repo.AccountChangeLogRepo;
import me.zohar.lottery.useraccount.repo.UserAccountRepo;

@Service
public class WithdrawService {

	@Autowired
	private WithdrawRecordRepo withdrawRecordRepo;

	@Autowired
	private UserAccountRepo userAccountRepo;

	@Autowired
	private AccountChangeLogRepo accountChangeLogRepo;

	/**
	 * 审核通过
	 * 
	 * @param id
	 */
	@ParamValid
	@Transactional
	public void approved(@NotBlank String id, String note) {
		WithdrawRecord withdrawRecord = withdrawRecordRepo.getOne(id);
		if (!Constant.提现记录状态_发起提现.equals(withdrawRecord.getState())) {
			throw new BizException(BizError.只有状态为发起提现的记录才能审核通过);
		}

		withdrawRecord.approved(note);
		withdrawRecordRepo.save(withdrawRecord);
	}

	/**
	 * 审核不通过
	 * 
	 * @param id
	 */
	@ParamValid
	@Transactional
	public void notApproved(@NotBlank String id, String note) {
		WithdrawRecord withdrawRecord = withdrawRecordRepo.getOne(id);
		if (!(Constant.提现记录状态_发起提现.equals(withdrawRecord.getState())
				|| Constant.提现记录状态_审核通过.equals(withdrawRecord.getState()))) {
			throw new BizException(BizError.只有状态为发起提现或审核通过的记录才能进行审核不通过操作);
		}

		withdrawRecord.notApproved(note);
		withdrawRecordRepo.save(withdrawRecord);
	}

	/**
	 * 确认到帐
	 * 
	 * @param id
	 */
	@ParamValid
	@Transactional
	public void confirmCredited(@NotBlank String id) {
		WithdrawRecord withdrawRecord = withdrawRecordRepo.getOne(id);
		if (!(Constant.提现记录状态_审核通过.equals(withdrawRecord.getState()))) {
			throw new BizException(BizError.只有状态为审核通过的记录才能进行确认到帐操作);
		}

		withdrawRecord.confirmCredited();
		withdrawRecordRepo.save(withdrawRecord);
	}

	@Transactional(readOnly = true)
	public PageResult<WithdrawRecordVO> findWithdrawRecordByPage(WithdrawRecordQueryCondParam param) {
		Specification<WithdrawRecord> spec = new Specification<WithdrawRecord>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<WithdrawRecord> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (StrUtil.isNotBlank(param.getOrderNo())) {
					predicates.add(builder.equal(root.get("orderNo"), param.getOrderNo()));
				}
				if (StrUtil.isNotBlank(param.getState())) {
					predicates.add(builder.equal(root.get("state"), param.getState()));
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
		Page<WithdrawRecord> result = withdrawRecordRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("submitTime"))));
		PageResult<WithdrawRecordVO> pageResult = new PageResult<>(WithdrawRecordVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

	@ParamValid
	@Transactional
	public void startWithdraw(StartWithdrawParam param) {
		UserAccount userAccount = userAccountRepo.getOne(param.getUserAccountId());
		if (!new BCryptPasswordEncoder().matches(param.getMoneyPwd(), userAccount.getMoneyPwd())) {
			throw new BizException(BizError.资金密码不正确);
		}
		double cashDeposit = NumberUtil.round(userAccount.getCashDeposit() - param.getWithdrawAmount(), 4).doubleValue();
		if (cashDeposit < 0) {
			throw new BizException(BizError.保证金余额不足);
		}
		if (userAccount.getBankInfoLatelyModifyTime() == null) {
			throw new BizException(BizError.银行卡未绑定无法进行提现);
		}

		WithdrawRecord withdrawRecord = param.convertToPo();
		withdrawRecord.setBankInfo(userAccount);
		withdrawRecordRepo.save(withdrawRecord);

		userAccount.setCashDeposit(cashDeposit);
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithStartWithdraw(userAccount, withdrawRecord));
	}

}
