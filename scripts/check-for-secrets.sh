#!/usr/bin/env bash

set -euo pipefail

failure=0

candidate_files() {
  git ls-files --cached --others --exclude-standard
}

sensitive_paths="$({
  candidate_files | grep -E '(^|/)(\.env($|\.)|secrets?(/|$)|credentials?(/|$))' || true
  candidate_files | grep -E '\.(pem|key|p12|pfx|jks|keystore|der)$' || true
  candidate_files | grep -E '(^|/)application-(local|secrets)(\.[^.]+)?\.(yaml|yml)$' || true
} | sort -u)"

if [[ -n "$sensitive_paths" ]]; then
  echo "Refusing to continue because sensitive file paths are tracked:"
  echo "$sensitive_paths" | sed 's/^/  - /'
  failure=1
fi

high_confidence_pattern='-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9_-]{20,}'

token_files="$(
  candidate_files \
    | grep -v '^scripts/check-for-secrets\.sh$' \
    | while IFS= read -r file; do
        if [[ -f "$file" ]] && rg -l --pcre2 "$high_confidence_pattern" "$file" >/dev/null 2>&1; then
          echo "$file"
        fi
      done
)"

if [[ -n "$token_files" ]]; then
  echo "Refusing to continue because possible private keys or access tokens were found in:"
  echo "$token_files" | sed 's/^/  - /'
  failure=1
fi

personal_path_pattern='/(Users|home)/[A-Za-z0-9._-]+/'

personal_path_files="$(
  candidate_files \
    | grep -v '^scripts/check-for-secrets\.sh$' \
    | while IFS= read -r file; do
        if [[ -f "$file" ]] && rg -l --pcre2 "$personal_path_pattern" "$file" >/dev/null 2>&1; then
          echo "$file"
        fi
      done
)"

if [[ -n "$personal_path_files" ]]; then
  echo "Refusing to continue because developer-specific absolute home paths were found in:"
  echo "$personal_path_files" | sed 's/^/  - /'
  failure=1
fi

configuration_files="$(
  candidate_files \
    | grep -E '\.(yaml|yml|properties|json)$' \
    | while IFS= read -r file; do
        if [[ -f "$file" ]] && rg -l --pcre2 -i \
          '(^|[[:space:]])(password|secret|api[_-]?key|access[_-]?token|private[_-]?key)[[:space:]]*[:=][[:space:]]*(?:\x22|\x27)?[^$<{[:space:]#][^[:space:]#]{7,}' \
          "$file" >/dev/null 2>&1; then
          echo "$file"
        fi
      done
)"

if [[ -n "$configuration_files" ]]; then
  echo "Refusing to continue because possible literal secrets were found in configuration files:"
  echo "$configuration_files" | sed 's/^/  - /'
  failure=1
fi

if [[ "$failure" -ne 0 ]]; then
  echo "Use environment variables or GitHub Actions secrets instead of committing secret values."
  exit 1
fi

echo "Secret scan passed: no tracked sensitive paths or high-confidence credentials found."
