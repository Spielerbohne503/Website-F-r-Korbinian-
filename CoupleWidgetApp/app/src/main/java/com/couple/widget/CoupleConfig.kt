package com.couple.widget

object CoupleConfig {
    // Unique ID for this couple — hardcoded into this APK
    const val COUPLE_ID = "7a3f9c2e1b5d4e8f9a0bc3d5e7f9"
    const val MQTT_BROKER = "tcp://broker.emqx.io:1883"

    // AES-128 encryption key (16 bytes) — private to this couple
    val AES_KEY = byteArrayOf(
        0x7a, 0x3f.toByte(), 0x9c.toByte(), 0x2e,
        0x1b, 0x5d, 0x4e, 0x8f.toByte(),
        0x9a.toByte(), 0x0b, 0xc3.toByte(), 0xd5.toByte(),
        0xe7.toByte(), 0xf9.toByte(), 0xa1.toByte(), 0xb3.toByte()
    )
}
