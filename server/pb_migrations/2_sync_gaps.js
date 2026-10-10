/// <reference path="../pb_data/types.d.ts" />

// Three things the sync needed to reach every phone:
// - `puzzles.updatedAt`: when the name, piece count or grid was last edited, so the newest edit wins on every phone.
// - `deletions`: what a phone deleted ("puzzle:<id>", "scan:<puzzle>:<date>", "photo:<puzzle>:<date>"), so the other phones delete it too instead of sending it back.
// - `puzzles.photoId`: the name of the box photo (it carries the date it was taken), so a retaken photo is noticed and the newest one wins.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")
  const own = "owner = @request.auth.id"

  const puzzles = app.findCollectionByNameOrId("puzzles")
  puzzles.fields.add(new TextField({ name: "photoId" }))
  puzzles.fields.add(new NumberField({ name: "updatedAt" }))
  app.save(puzzles)

  const deletions = new Collection({
    type: "base",
    name: "deletions",
    listRule: own,
    viewRule: own,
    createRule: "@request.auth.id != '' && @request.body.owner = @request.auth.id",
    updateRule: null,
    deleteRule: own,
    fields: [
      { name: "owner", type: "relation", required: true, collectionId: users.id, cascadeDelete: true, maxSelect: 1 },
      { name: "key", type: "text", required: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_deletions_owner_key ON deletions (owner, key)"],
  })
  app.save(deletions)
}, (app) => {
  app.delete(app.findCollectionByNameOrId("deletions"))
  const puzzles = app.findCollectionByNameOrId("puzzles")
  puzzles.fields.removeByName("photoId")
  puzzles.fields.removeByName("updatedAt")
  app.save(puzzles)
})
