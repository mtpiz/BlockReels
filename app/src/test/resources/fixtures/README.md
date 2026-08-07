# Dump fixtures

Real accessibility-tree captures, replayed against the detectors by `FixtureTest`.

**Every file here should come off a real phone.** Capture with dump mode (see the project
README), share the file out of the app, drop it in this directory, and name it for the
verdict you expect:

```
instagram-reels.BLOCK.txt
instagram-dm-thread.ALLOW.txt
instagram-story-viewer.ALLOW.txt
instagram-home-feed.BLOCK.txt
instagram-explore.BLOCK.txt
instagram-profile.ALLOW.txt
youtube-shorts-player.BLOCK.txt
youtube-watch-longform.ALLOW.txt
youtube-home-with-shorts-shelf.ALLOW.txt
```

No test code needs writing — `FixtureTest` picks up whatever is here. `BLOCK` asserts the
detector fires; `ALLOW` asserts it does not, whether by an explicit allow-rule or by having
no opinion.

The point of this directory is that detector work stops needing a device. Once a surface is
captured, its detector can be rewritten and re-checked offline, and no later repair can
silently break it.

Dumps contain view ids, class names, content descriptions and any on-screen **text** —
which for a DM capture means the messages. Read a dump before committing it and trim
anything you'd rather not have in git history. The ids are what matters; the text is not.

The `synthetic-*` files are hand-written to exercise the harness itself and are marked as
such. Delete them once real captures exist.
