# Contributing

Pull requests are welcome. Every PR is reviewed before it is merged.

## Language

Write everything in English: code, comments, commit messages, PR title and description.
The only exception is the UI text, which stays in Spanish like go-data itself.

## Pull request description

Every PR must include these three sections:

```markdown
## Problem
What is wrong or missing, and how to reproduce it (steps, screenshots, server version).

## Solution
What you changed and why. Mention any trade-off or anything left out.

## Tests
Which tests you added or changed, and how you checked it on a real device or server.
```

PRs without a clear problem, solution and tests will be sent back.

## Tests

- Run the unit tests and build before opening the PR:

  ```sh
  ./docker/build-apk.sh
  ```

- New logic (parsing, formatting, state changes) needs a unit test in `app/src/test/`.
- UI changes need a screenshot or short video in the PR.
- If you touch the API client, also run the live test against a real server (see the README).

## Keep the code structure

Follow the layout and style already in the repo:

- `Api.kt`: HTTP client and endpoints. `Model.kt`: data classes for go-data's JSON.
  `AppViewModel.kt`: state and polling. `Format.kt`: formatting helpers.
- `ui/`: the app's components, screens and section views.
- `lumo/`: theme and Lumo UI components. Reuse them before adding new ones. Use `Dimens` and the
  theme colors instead of hardcoded values.
- Put new code in the file where similar code already lives. Do not add new layers or packages
  unless the PR explains why.
- Do not add dependencies unless the PR explains why the existing ones are not enough.
- Keep PRs small and focused on one change. No unrelated refactors or formatting changes.
- Commit messages use [Conventional Commits](https://www.conventionalcommits.org/):
  `feat: ...`, `fix: ...`, `docs: ...`.

## License

By contributing, you agree that your code is licensed under the [MIT License](LICENSE).
