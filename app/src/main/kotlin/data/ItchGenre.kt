package garden.appl.mitch.data

import androidx.annotation.StringRes
import garden.appl.mitch.R

enum class ItchGenre(@StringRes val nameResource: Int) {
    ACTION(R.string.genre_action),
    ADVENTURE(R.string.genre_adventure),
    CARD_GAME(R.string.genre_card_game),
    EDUCATIONAL(R.string.genre_educational),
    FIGHTING(R.string.genre_fighting),
    INTERACTIVE_FICTION(R.string.genre_interactive_fiction),
    PLATFORMER(R.string.genre_platformer),
    PUZZLE(R.string.genre_puzzle),
    RACING(R.string.genre_racing),
    RHYTHM(R.string.genre_rhythm),
    ROLE_PLAYING(R.string.genre_rpg),
    SHOOTER(R.string.genre_shooter),
    SIMULATION(R.string.genre_simulation),
    SPORTS(R.string.genre_sports),
    STRATEGY(R.string.genre_strategy),
    SURVIVAL(R.string.genre_survival),
    VISUAL_NOVEL(R.string.genre_visual_novel)
}