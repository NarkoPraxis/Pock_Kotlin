package enums

enum class GameState {
    Play,
    Scored,
    CountDown, // dead state — CountDown removed
    BallSelection,
    Tutorial, // dead state
    GameOver,
    Temp,
}
