# Main branch policy

The repository's default branch must be changed to `main` after the current work is
committed and pushed. Apply these GitHub rules to `main`:

- require pull requests before merging;
- require the branch to be up to date before merging;
- require the `verify`, `Secret scan`, and `CodeQL` checks to pass;
- block force-pushes and branch deletion;
- allow direct pushes only for emergency repository administration.

`Dependency review` runs only for pull requests, so it should be required as a pull-request
check when the repository ruleset supports event-specific checks. A mandatory reviewer is
optional for this single-maintainer repository; the CI checks and force-push protection are
the important controls.

This file documents the policy; keep the GitHub branch protection settings synchronized with it.
