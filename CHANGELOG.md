# Unreleased

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
