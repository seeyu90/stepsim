package com.seeyu90.stepsim.simulator

import java.time.Instant

/** 可注入的時間來源；simulator 一律透過它取得現在時間，單元測試才能控制時間。 */
fun interface TimeSource {
    fun now(): Instant
}

/** 正式環境的時間來源，是 simulator 中唯一呼叫 [Instant.now] 的地方。 */
object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
}
