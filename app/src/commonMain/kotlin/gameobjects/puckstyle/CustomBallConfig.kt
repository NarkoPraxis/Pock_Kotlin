package gameobjects.puckstyle

import enums.BallType

data class CustomBallConfig(
    val skinType: BallType,
    val tailType: BallType,
    val paddleType: BallType,
    val skinZRank: Int,   // 0 = drawn first (back), 2 = drawn last (front)
    val tailZRank: Int,
    val paddleZRank: Int
)
