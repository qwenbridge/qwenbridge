#!/usr/bin/env bash
# Sourced by scripts/verify-release.sh (not executed directly).
print_summary() {
  echo ""
  echo "======================================================"
  echo "                 VERIFY RELEASE SUMMARY"
  echo "======================================================"

  echo -e "${GREEN}Passed:${NC} ${PASSED}"
  echo -e "${YELLOW}Warnings:${NC} ${WARNINGS}"
  echo -e "${YELLOW}Skipped:${NC} ${SKIPPED}"
  echo -e "${RED}Failed:${NC} ${FAILED}"

  if [[ "${FAILED}" -eq 0 ]]; then
    echo -e "${GREEN}RESULT: RELEASE VERIFICATION PASSED${NC}"
    return 0
  fi

  echo -e "${RED}RESULT: RELEASE VERIFICATION FAILED${NC}"
  return 1
}
