package es.saniexam.app.scheduler

/**
 * FSRS card lifecycle phases. Mirrors ts-fsrs `State` enum. The order
 * is the engine's FSM (New -> Learning/Review -> Relearning -> Review);
 * a card always starts in [New] and never returns to it.
 */
enum class CardPhase {
    New,
    Learning,
    Review,
    Relearning,
}
