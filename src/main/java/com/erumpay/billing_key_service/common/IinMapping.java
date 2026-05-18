package com.erumpay.billing_key_service.common;

import java.util.Map;

// IIN(카드번호 앞 6자리) 기반 카드사 식별 더미 매핑. 운영 진입 시 DB 이관 검토.
public class IinMapping {

    private static final Map<String, CardCompany> IIN_TO_CARD_COMPANY = Map.ofEntries(
        Map.entry("123456", CardCompany.SHINHAN),
        Map.entry("234567", CardCompany.SAMSUNG),
        Map.entry("345678", CardCompany.HYUNDAI),
        Map.entry("456789", CardCompany.KB),
        Map.entry("567890", CardCompany.LOTTE),
        Map.entry("678901", CardCompany.WOORI),
        Map.entry("789012", CardCompany.HANA),
        Map.entry("890123", CardCompany.NH),
        Map.entry("901234", CardCompany.BC)
    );

    private IinMapping() {}

    public static CardCompany findByCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return CardCompany.UNKNOWN;
        }
        String normalized = cardNumber.replaceAll("\\D", "");
        if (normalized.length() < 6) {
            return CardCompany.UNKNOWN;
        }
        String iin = normalized.substring(0, 6);
        return IIN_TO_CARD_COMPANY.getOrDefault(iin, CardCompany.UNKNOWN);
    }
}
