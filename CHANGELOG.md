# 0.9.28 (2025-11-25 / 8339af2)

## Fixed

- Fix broken error handling

# 0.8.25 (2025-11-18 / 48e4a4f)

## Changed

- Arguments are now passed on through middleware

# 0.7.22 (2025-11-18 / f622ad5)

## Changed

- remove debug code

# 0.6.19 (2025-11-18 / ecc1426)

## Changed

- make `worker-info-dao` private

# 0.5.15 (2025-11-14 / 2b66139)

## Added

- Added convenience functions: `workers-info`, `unregister-worker`
- Add middleware capabilities

# 0.4.11 (2025-10-20 / 14da89a)

## Fixed

- Handle passing clojure maps as arguments, they get converted back from java
  maps, and keywords get restored
- When inspecting the queue contents, return the var as a symbol, not a string

# 0.3.7 (2025-09-10 / 9ade774)

- First version
