package com.yunxigames;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 随机换位包入口（yg_swap）：受伤随机互换位置。
 *
 * <p>本包自带 yg-core 基础库与自己的配置（{@code config/yg-swap.json}），可独立安装。
 * 玩法零持久化（换位是瞬时的），唯一内存态是「无对象提示」的限频表，关服时清掉。
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

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HurtSwap.clearTransientState());

		LOGGER.info("[yg-swap] 随机换位已加载");
	}
}
