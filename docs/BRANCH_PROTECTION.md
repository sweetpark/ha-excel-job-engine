# 🛡️ Main 브랜치 PR 보호 규칙 설정 가이드 (Branch Protection Rules)

`ha-excel-job-engine`은 오픈소스 및 엔터프라이즈 환경에서 안정적인 릴리즈 품질을 보장하기 위해 `main` 브랜치에 대한 **PR 기반 브랜치 보호 규칙(Branch Protection Rules)** 적용을 적극 권장합니다.

---

## 📑 1. 권장 브랜치 보호 규칙 요약

| 항목 | 권장 설정값 | 목적 |
|---|:---:|---|
| **Require a pull request before merging** | ✅ 활성화 | `main` 브랜치로의 직접 `git push` 차단, PR 강제 |
| └ **Require approvals** | `1`명 이상 | 최소 1인 이상의 코드 리뷰 승인 필수 |
| └ **Dismiss stale pull request approvals when new commits are pushed** | ✅ 활성화 | 새로운 커밋이 추가되면 이전 승인을 자동 무효화하여 재검토 유도 |
| **Require status checks to pass before merging** | ✅ 활성화 | CI 품질 게이트 통과 전 머지 차단 |
| └ **Status checks: `build (17)`** | 필수 체크 | Java 17 빌드, 테스트 29개, JaCoCo, Spotless, SpotBugs 통과 확인 |
| └ **Status checks: `build (21)`** | 필수 체크 | Java 21 LTS 런타임 호환성 검증 통과 확인 |
| └ **Require branches to be up to date before merging** | ✅ 활성화 | 최신 `main` 브랜치와 동기화된 상태에서만 머지 허용 |
| **Require conversation resolution before merging** | ✅ 활성화 | PR 코드 리뷰 코멘트가 모두 해결(Resolved)되어야 머지 가능 |
| **Require linear history** | ✅ 활성화 | 복잡한 머지 커밋 방지 (깔끔한 커밋 히스토리 유지) |
| **Do not allow bypassing the above settings** | ✅ 활성화 | 저장소 관리자(Admin)도 위 규칙을 우회할 수 없도록 강제 |

---

## 🖥️ 2. GitHub Web UI 설정 방법

1. GitHub 저장소 상단 탭에서 **`Settings`** 클릭.
2. 좌측 메뉴에서 **`Code and automation`** ➔ **`Branches`** 클릭.
3. **`Branch protection rules`** 섹션의 **`Add branch protection rule`** (또는 기존 rule의 Edit) 버튼 클릭.
4. **Branch name pattern**에 `main` 입력.
5. 아래 옵션들을 체크합니다:
   - [x] **Require a pull request before merging**
     - [x] **Require approvals** (수치: `1`)
     - [x] **Dismiss stale pull request approvals when new commits are pushed**
   - [x] **Require status checks to pass before merging**
     - [x] **Require branches to be up to date before merging**
     - **Status checks that are required** 검색창에서 아래 2개 체크 선택:
       - `build (17)`
       - `build (21)`
   - [x] **Require conversation resolution before merging**
   - [x] **Require linear history**
   - [x] **Do not allow bypassing the above settings** (Enforce for administrators)
6. 페이지 하단의 **`Save changes`** 클릭 (비밀번호 또는 2FA 인증).

---

## 🔀 3. 저장소 PR 머지 설정 (Pull Requests Settings)

자동 시맨틱 릴리즈(`github-tag-action`)가 정상 동작하기 위해서는 커밋 메시지 타이틀이 컨벤션(`feat:`, `fix:`)을 유지해야 합니다:

1. **`Settings`** ➔ **`General`** 클릭.
2. **`Pull Requests`** 섹션으로 이동:
   - [x] **Allow squash merging** (활성화)
     - Default commit message: **`Pull request title and description`** 선택
   - [ ] **Allow merge commits** (비활성화 권장)
   - [ ] **Allow rebase merging** (선택 사항)
   - [x] **Automatically delete head branches** (활성화: PR 머지 후 기능 브랜치 자동 삭제)

---

## ⚡ 4. GitHub CLI (`gh`) 원클릭 설정 명령어

GitHub CLI(`gh`)가 로그인되어 있다면 터미널에서 아래 명령어로 즉시 브랜치 보호 규칙을 적용할 수 있습니다:

```bash
gh api --method PUT repos/sweetpark/ha-excel-job-engine/branches/main/protection   --input - << 'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "build (17)",
      "build (21)"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
EOF
```
