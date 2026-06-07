# Git Convention

## Branch

```
prod                            # production-ready only
dev                             # integration branch
feature/<issue-number>-<slug>   # new feature
fix/<issue-number>-<slug>       # bug fix
refactor/<issue-number>-<slug>  # refactoring only
docs/<issue-number>-<slug>      # documentation
chore/<slug>                    # build, deps, config
```

- slug: lowercase kebab-case (e.g. add-login-api)
- No direct push to `prod` or `dev`, PR only

## Commit

```
<type>(<scope>): <subject>
```

Types: feat | fix | refactor | chore | docs | style

Scopes:

| scope | target |
|-------|--------|
| `member` | services/member-service |
| `auth` | services/auth-service |
| `gateway` | services/gateway-service |
| `eureka` | services/eureka-server |
| `common` | common/* |

- Start with lowercase verb: add, remove, update, fix
- No period at end, under 50 chars
- Add body when the reason for the change is not obvious

## Issue

### Title

```
[type] short description
```

Types: bug | feature | refactor | docs | chore

Examples:
- `[bug] gateway CircuitBreaker duplicate code`
- `[feature] add file upload API`
- `[refactor] extract common resilience4j config`

### Rules

- Create an issue before starting any work
- Reference the issue number in the branch name and commit message
- Link the issue in the PR body with `Closes #<issue-number>`

## Pull Request

- Title: same format as commit message
- One PR = one purpose
- dev → prod: Squash merge
- feature → dev: Merge commit
