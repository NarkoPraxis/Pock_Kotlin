package enums

/**
 * Which of the two Ball Designer panes the player last had open, so tapping into the designer from
 * the main menu reopens the one they left (persisted via Storage.ballDesignerPane).
 *
 * [Style] — the Ball Designer style screen (BallDesignerScreen / CBC).
 * [Color] — the Color Picker screen (BallDesignerColorScreen / CCP).
 */
enum class DesignerPane { Style, Color }
