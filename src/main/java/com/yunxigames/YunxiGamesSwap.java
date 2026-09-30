package com.yunxigames;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 随机换位包入口（yg_swap）：受伤随机互换位置。
 *
 * <p>本包自带 yg-core 基础库与自己的配置（{@code config/yg-swap.json}），可独立安装。
 * 没有任何持久化状态（换位是瞬时的，无内存清单），关服即新的一局，
 * 所以不需要注册 SERVER_STOPPING 清理。
 */
public class YunxiGamesSwap implements ModInitializer {
	public static final String MOD_ID = "yg_swap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SwapConfig.load();

		HurtSwap.register();

		// 自检（编号包内局部）：一次跑完 ① 配置钳制 ② 互换+清摔落 ③ 对象筛选
		SelfTest.registerStep("㊱ 随机换位·配置+互换+筛选",
				ctx -> SwapSelfTest.checkAll(ctx.server.overworld(), ctx.pos, SwapConfig.get()));
		SelfTest.register(() -> SwapConfig.get().selfTestRolls);

		LOGGER.info("[yg-swap] 随机换位已加载");
	}
}
