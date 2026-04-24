package enums

enum class GameState {
    Play,
    Scored,
    CountDown, // dead state — CountDown removed in Plan 15
    Tutorial,
    BallSelection,
    GameOver,
    Temp,
}
