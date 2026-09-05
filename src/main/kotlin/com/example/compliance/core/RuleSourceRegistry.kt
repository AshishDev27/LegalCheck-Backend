package com.example.compliance.core

object RuleSourceRegistry {
    const val LMPC_RULES_2011 = "Legal Metrology (Packaged Commodities) Rules, 2011"
    const val LM_ACT_2009 = "Legal Metrology Act, 2009"
    const val AMENDMENT_2026 = "Legal Metrology (Packaged Commodities) Third Amendment Rules, 2026 [G.S.R. 418(E)]"

    fun getOfficialCitation(ruleNumber: String, source: String): String {
        return "As per Rule $ruleNumber of $source."
    }
}
