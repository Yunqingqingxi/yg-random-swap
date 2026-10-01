package com.yunxigames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.yunxigames.SwapConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /yg swap ...}：随机换位玩法（受伤换位）的游戏内启停与状态。
 *
 * <p>各玩法包统一往 {@code /yg} 根下挂以玩法名命名的子树（Brigadier 会把各包注册的
 * 同名根节点合并成一棵命令树），与 drops 包的 {@code /yg drops} 同一布局。
 * 换位判定实时读配置，off 立即生效。
 */
public final class SwapCommand {
	/** 与 drops 包同一权限档（等价旧「权限等级 2」，OP 可用）。 */
	private static final PermissionCheck PERMISSION = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

	private SwapCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("yg")
				.requires(Commands.hasPermission(PERMISSION))
				.then(Commands.literal("swap")
						.executes(context -> status(context.getSource()))
						.then(Commands.literal("on")
								.executes(context -> toggle(context.getSource(), true)))
						.then(Commands.literal("off")
								.executes(context -> toggle(context.getSource(), false)))));
	}

	private static int toggle(CommandSourceStack source, boolean enabled) {
		SwapConfig config = SwapConfig.get();
		config.hurtSwapEnabled = enabled;
		config.save();
		source.sendSuccess(() -> Component.literal("[yg] 随机换位玩法：" + (enabled ? "开启" : "关闭")), false);
		return status(source);
	}

	private static int status(CommandSourceStack source) {
		SwapConfig config = SwapConfig.get();
		source.sendSuccess(() -> Component.literal(String.format(
				"[yg] 随机换位玩法=%s | 半径=%.0f格 含玩家=%s 清摔落=%s 播报=%s",
				config.hurtSwapEnabled ? "开" : "关",
				config.hurtSwapMaxRadius,
				config.hurtSwapIncludePlayers ? "是" : "否",
				config.hurtSwapClearFallDistance ? "是" : "否",
				config.hurtSwapAnnounce ? "开" : "关")), false);
		return 1;
	}
}
