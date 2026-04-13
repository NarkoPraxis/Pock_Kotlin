package utility

object GameEvents {
    val canScore: Signal<Unit> = Signal()
    val cantScore: Signal<Unit> = Signal()
    val gameOver: Signal<Unit> = Signal()
    val gameReset: Signal<Unit> = Signal()
}
