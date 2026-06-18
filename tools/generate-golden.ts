/*
 * Generate the FSRS v6 golden test fixture for SaniExam from a pinned
 * reference implementation. The output is written to
 *   app/src/test/resources/scheduler/golden/fsrs-cases.json
 * and is consumed by `FsrsSchedulerGoldenTest`.
 *
 * Reference: ts-fsrs@5.4.1 (FSRS-6, MIT). Pin reason: this is the
 * current `npm install ts-fsrs@latest` at the time of the PR2
 * remediation; its `default_w` exports the same 21-element FSRS-6
 * default that `open-spaced-repetition/fsrs-kotlin` README documents.
 * `enable_fuzz=false`, `enable_short_term=true`,
 * `learning_steps=['1m','10m']`, `relearning_steps=['10m']` — all
 * ts-fsrs defaults; baked in.
 *
 * If a future maintainer re-tunes the `w` vector or the algorithm
 * itself, bump `SchedulerVersion.CURRENT` and re-run this script; the
 * fixture's `generator` and `parameters.w` metadata are also asserted
 * by `FsrsSchedulerGoldenTest` so silent drift fails CI.
 *
 * Usage:
 *   cd tools
 *   npm install ts-fsrs@5.4.1
 *   node generate-golden.ts > ../app/src/test/resources/scheduler/golden/fsrs-cases.json
 *
 * Drift policy: the output is committed verbatim; if a code change
 * changes a computed field, regenerate this file in the SAME commit as
 * the engine change. CI fails on field-level diff.
 */

const fs = require('fs');
const path = require('path');
const {
  fsrs,
  createEmptyCard,
  Rating,
  State,
  generatorParameters,
  version,
} = require('ts-fsrs');

const params = generatorParameters({
  enable_fuzz: false,
  enable_short_term: true,
});
const f = fsrs(params);

const ms = (d) => new Date(d).getTime();

function cardSnapshot(card) {
  return {
    state: State[card.state],
    due: ms(card.due),
    stability: card.stability,
    difficulty: card.difficulty,
    elapsed_days: card.elapsed_days,
    scheduled_days: card.scheduled_days,
    reps: card.reps,
    lapses: card.lapses,
    learning_steps: card.learning_steps,
    last_review: card.last_review ? ms(card.last_review) : null,
  };
}

function logSnapshot(log) {
  return {
    rating: Rating[log.rating],
    state: State[log.state],
    due: ms(log.due),
    stability: log.stability,
    difficulty: log.difficulty,
    elapsed_days: log.elapsed_days,
    last_elapsed_days: log.last_elapsed_days,
    scheduled_days: log.scheduled_days,
    review: ms(log.review),
  };
}

function gradeSnapshot(recordLogItem) {
  return { card: cardSnapshot(recordLogItem.card), log: logSnapshot(recordLogItem.log) };
}

function pickGrade(recordLog, ratingName) {
  return gradeSnapshot(recordLog[Rating[ratingName]]);
}

const cases = [];

// 1) Cold-start: empty New card, every rating.
{
  const now = new Date('2025-01-15T10:00:00.000Z');
  const card = createEmptyCard(now);
  const record = f.repeat(card, now);
  for (const rating of ['Again', 'Hard', 'Good', 'Easy']) {
    cases.push({
      id: `new-${rating.toLowerCase()}`,
      description: `New card rated ${rating}`,
      input: { card: cardSnapshot(card), rating, now: ms(now) },
      output: pickGrade(record, rating),
    });
  }
}

// 2) Mature Review card (3 prior Good reviews), every rating.
{
  const now0 = new Date('2025-01-15T10:00:00.000Z');
  let card = createEmptyCard(now0);
  card = f.repeat(card, now0)[Rating.Good].card;
  card = f.repeat(card, new Date('2025-01-16T10:00:00.000Z'))[Rating.Good].card;
  card = f.repeat(card, card.due)[Rating.Good].card;
  card = f.repeat(card, card.due)[Rating.Good].card;
  const now3 = card.due;
  const r4 = f.repeat(card, now3);
  for (const rating of ['Again', 'Hard', 'Good', 'Easy']) {
    cases.push({
      id: `review-${rating.toLowerCase()}`,
      description: `Mature Review card rated ${rating}`,
      input: { card: cardSnapshot(card), rating, now: ms(now3) },
      output: pickGrade(r4, rating),
    });
  }
}

// 3) Relearning card (Again on a mature card), every rating at scheduled due.
{
  const now0 = new Date('2025-01-15T10:00:00.000Z');
  let card = createEmptyCard(now0);
  card = f.repeat(card, now0)[Rating.Good].card;
  card = f.repeat(card, new Date('2025-01-16T10:00:00.000Z'))[Rating.Good].card;
  card = f.repeat(card, card.due)[Rating.Good].card;
  card = f.repeat(card, card.due)[Rating.Good].card;
  const rAgain = f.repeat(card, card.due)[Rating.Again];
  const relearningCard = rAgain.card;
  const relearningDue = relearningCard.due;
  const rRelearn = f.repeat(relearningCard, relearningDue);
  for (const rating of ['Again', 'Hard', 'Good', 'Easy']) {
    cases.push({
      id: `relearning-${rating.toLowerCase()}`,
      description: `Relearning card rated ${rating}`,
      input: { card: cardSnapshot(relearningCard), rating, now: ms(relearningDue) },
      output: pickGrade(rRelearn, rating),
    });
  }
}

// 4) Edge: rating at exactly scheduled due (zero elapsed beyond due).
{
  const now0 = new Date('2025-01-15T10:00:00.000Z');
  let card = createEmptyCard(now0);
  card = f.repeat(card, now0)[Rating.Good].card;
  card = f.repeat(card, new Date('2025-01-16T10:00:00.000Z'))[Rating.Good].card;
  const rSame = f.repeat(card, card.due);
  for (const rating of ['Again', 'Hard', 'Good', 'Easy']) {
    cases.push({
      id: `zero-elapsed-${rating.toLowerCase()}`,
      description: `Review card rated ${rating} at exactly its scheduled due (zero elapsed beyond due)`,
      input: { card: cardSnapshot(card), rating, now: ms(card.due) },
      output: pickGrade(rSame, rating),
    });
  }
}

const out = {
  generator: `ts-fsrs@${version} (FSRS-6 / FSRS v6)`,
  parameters: {
    request_retention: params.request_retention,
    maximum_interval: params.maximum_interval,
    enable_fuzz: params.enable_fuzz,
    enable_short_term: params.enable_short_term,
    learning_steps: params.learning_steps,
    relearning_steps: params.relearning_steps,
    w: params.w,
  },
  cases,
};

const dest = path.join(
  __dirname,
  '..',
  'app',
  'src',
  'test',
  'resources',
  'scheduler',
  'golden',
  'fsrs-cases.json',
);
fs.mkdirSync(path.dirname(dest), { recursive: true });
fs.writeFileSync(dest, JSON.stringify(out, null, 2) + '\n');
process.stdout.write(`wrote ${cases.length} cases to ${dest}\n`);
