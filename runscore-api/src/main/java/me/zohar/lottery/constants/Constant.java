package me.zohar.lottery.constants;

public class Constant {

	public static final String 申诉类型_未支付申请取消订单 = "1";

	public static final String 申诉类型_实际支付金额小于收款金额 = "2";

	public static final String 申诉状态_待处理 = "1";

	public static final String 申诉状态_已完结 = "2";

	public static final String 申诉处理方式_用户撤销申诉 = "1";

	public static final String 申诉处理方式_改为实际支付金额 = "2";
	
	public static final String 申诉处理方式_取消订单 = "3";
	
	public static final String 申诉处理方式_不做处理 = "4";

	public static final String 接单状态_正在接单 = "1";

	public static final String 接单状态_停止接单 = "2";

	public static final String 商户订单状态_等待接单 = "1";

	public static final String 商户订单状态_已接单 = "2";

	public static final String 商户订单状态_商户已确认支付 = "3";

	public static final String 商户订单状态_已支付 = "4";

	public static final String 商户订单状态_超时取消 = "5";

	public static final String 商户订单状态_人工取消 = "6";
	
	public static final String 商户订单状态_商户取消订单 = "7";

	public static final String 商户订单状态_客服取消订单退款 = "9";

	public static final String 账号类型_管理员 = "admin";

	public static final String 账号类型_代理 = "agent";

	public static final String 账号类型_会员 = "member";

	public static final String 账号类型_商户 = "merchant";

	public static final String 账号状态_启用 = "1";

	public static final String 账号状态_禁用 = "2";

	public static final Integer 充值订单默认有效时长 = 10;

	public static final String 充值订单_已支付订单单号 = "RECHARGE_ORDER_PAID_ORDER_NO";

	public static final String 充值订单状态_待支付 = "1";

	public static final String 充值订单状态_已支付 = "2";

	public static final String 充值订单状态_已结算 = "3";

	public static final String 充值订单状态_超时取消 = "4";

	public static final String 充值订单状态_人工取消 = "5";

	public static final String 账变日志类型_账号充值 = "1";

	public static final String 账变日志类型_充值优惠 = "2";

	public static final String 账变日志类型_接单扣款 = "3";

	public static final String 账变日志类型_接单奖励金 = "4";

	public static final String 账变日志类型_账号提现 = "5";

	public static final String 账变日志类型_退还保证金 = "6";

	public static final String 账变日志类型_手工增保证金 = "8";

	public static final String 账变日志类型_手工减保证金 = "9";

	public static final String 账变日志类型_改单为实际支付金额并退款 = "10";
	
	public static final String 账变日志类型_客服取消订单并退款 = "11";

	public static final String 充提日志订单类型_充值 = "1";

	public static final String 充提日志订单类型_提现 = "2";

	public static final String 提现记录状态_发起提现 = "1";

	public static final String 提现记录状态_审核通过 = "2";

	public static final String 提现记录状态_审核不通过 = "3";

	public static final String 提现记录状态_已到账 = "4";

	public static final String 平台订单_已支付订单单号 = "PLATFORM_ORDER_PAID_ORDER_NO";

	public static final Integer 平台订单默认有效时长 = 10;

}
