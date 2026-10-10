/// <reference path="../pb_data/types.d.ts" />

// PuzzleIt backend: every record belongs to one user (`owner`) and only that user can read or change it.
// Photos are protected files: they are downloaded with a short-lived token, never with a public link.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const own = "owner = @request.auth.id"
  const rules = {
    listRule: own,
    viewRule: own,
    createRule: "@request.auth.id != '' && @request.body.owner = @request.auth.id",
    updateRule: own,
    deleteRule: own,
  }
  const photo = (name) => ({ name, type: "file", maxSelect: 1, maxSize: 30 * 1024 * 1024, protected: true })

  const puzzles = new Collection({
    type: "base",
    name: "puzzles",
    ...rules,
    fields: [
      { name: "owner", type: "relation", required: true, collectionId: users.id, cascadeDelete: true, maxSelect: 1 },
      // The id the puzzle has on the phone: a second phone finds its puzzles again with it.
      { name: "localId", type: "text", required: true },
      { name: "name", type: "text", required: true },
      { name: "pieces", type: "number" },
      { name: "gridRows", type: "number" },
      { name: "gridCols", type: "number" },
      { name: "difficulty", type: "text" },
      { name: "createdAt", type: "number" },
      { name: "puzzleQuad", type: "text" },
      { name: "status", type: "text" },
      photo("image"),
      photo("warped"),
      photo("thumb"),
    ],
    indexes: ["CREATE UNIQUE INDEX idx_puzzles_owner_local ON puzzles (owner, localId)"],
  })
  app.save(puzzles)

  const scans = new Collection({
    type: "base",
    name: "scans",
    ...rules,
    fields: [
      { name: "owner", type: "relation", required: true, collectionId: users.id, cascadeDelete: true, maxSelect: 1 },
      { name: "puzzle", type: "relation", required: true, collectionId: puzzles.id, cascadeDelete: true, maxSelect: 1 },
      // The scan's date in milliseconds is its key within the puzzle.
      { name: "createdAt", type: "number", required: true },
      { name: "leads", type: "text" },
      { name: "verdict", type: "text" },
      { name: "chosenLead", type: "number" },
      photo("piece"),
    ],
    indexes: ["CREATE UNIQUE INDEX idx_scans_puzzle_date ON scans (puzzle, createdAt)"],
  })
  app.save(scans)

  const progress = new Collection({
    type: "base",
    name: "progress_photos",
    ...rules,
    fields: [
      { name: "owner", type: "relation", required: true, collectionId: users.id, cascadeDelete: true, maxSelect: 1 },
      { name: "puzzle", type: "relation", required: true, collectionId: puzzles.id, cascadeDelete: true, maxSelect: 1 },
      { name: "createdAt", type: "number", required: true },
      photo("photo"),
    ],
    indexes: ["CREATE UNIQUE INDEX idx_progress_puzzle_date ON progress_photos (puzzle, createdAt)"],
  })
  app.save(progress)
}, (app) => {
  for (const name of ["progress_photos", "scans", "puzzles"]) app.delete(app.findCollectionByNameOrId(name))
})
