# Why commit 3892b0b78df3fe176da982cd290c2bf979692266 was needed

## Short answer

It was needed to stabilize the seed baseline used by API v2 tests.

This commit changes only:
- `datalayer/db/seeds.rb`
- `datalayer/db/seeds.pgbin`

## Root cause it addressed

With the old seed setup, restored test data could miss bootstrap rows expected by specs:
- signed-in users auth group (`SIGNED_IN_USERS_GROUP_ID`)
- deterministic setup of the `password` auth system row

That caused mismatches like:
- group-related expectations (`201` vs `202`, missing first group)
- audit bootstrap differences (presence/absence of group bootstrap insert)

## What the commit changed

In `datalayer/db/seeds.rb` it:
- creates or updates `AuthSystem(id: 'password')` explicitly
- creates `AuthenticationGroup` with `SIGNED_IN_USERS_GROUP_ID`
- then `db/seeds.pgbin` was regenerated from that seed state

This is important because tests restore from `datalayer/db/seeds.pgbin`, not from `seeds.rb` directly.

It is seed/bootstrap data determinism:
- which rows exist in the restored seed dump,
- and in what bootstrap shape audit/group-dependent tests start.
