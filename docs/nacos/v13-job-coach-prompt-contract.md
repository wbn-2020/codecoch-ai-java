# V13 Job Coach Prompt Contract

The V13 skill-gap context affects the managed Prompt scene
`JOB_COACH_DAILY_PLAN`.

Code-side enforcement accepts a database-managed ACTIVE version only when all of
the following are true:

- `version_code` is `v13-agent-skill-gap-context` or starts with
  `v13-agent-skill-gap-context-`;
- Prompt content declares and uses `contextJson`, `candidatesJson`, `taskCount`,
  and `maxTotalMinutes`;
- Prompt content contains the stable semantic marker `SKILL_GAP_ITEM`;
- the normal Prompt placeholder/declaration validation passes.

If a stale ACTIVE version appears after startup, rendering ignores it and uses
the built-in V13 Prompt. At startup, `codecoachai.ai.prompt-contract.fail-fast`
causes an incompatible ACTIVE version to fail the release instead of silently
overriding the built-in Prompt.

The forward migration owned by the main integration branch must:

1. Create an idempotent version for `JOB_COACH_DAILY_PLAN` with the compatible
   version code, required placeholders, variable declaration, and
   `SKILL_GAP_ITEM` instructions.
2. Mark prior active versions for the same template inactive.
3. Activate the new version and update `prompt_template.active_version_id`,
   content, variables, version, enabled, and status in the same migration.
4. Preserve the currently active model parameters unless the release explicitly
   changes them.
5. Add a migration contract test that proves the final ACTIVE row satisfies the
   code contract.
