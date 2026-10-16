package com.seeyu90.stepsim.simulator

/**
 * 步態與步頻（步/分）。
 * - 走路 100：成人步行研究常用的中等強度步頻門檻，日常快走多在 100–120。
 * - 跑步 160：一般休閒跑者多在 150–170；180 是菁英跑者的數字，當預設偏高。
 */
enum class Gait(val stepsPerMinute: Int) {
    WALK(100),
    RUN(160),
}
