package com.with_kim.aloc_study.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.with_kim.aloc_study.dto.HouseSearchFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class QueryParserClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String model;

    public QueryParserClient(@Qualifier("openAiRestClient") RestClient restClient,
                             @Value("${openai.parser-model}") String model){
        this.restClient=restClient;
        this.model=model;
    }

    private static final String SYSTEM_PROMPT= """
            당신은 부동산 검색 쿼리 분석기입니다.
            사용자의 자연어 검색 문장에서 조건을 추출해 JSON으로만 응답하세요.
            설명, 마크다운, 코드블록 없이 순수 JSON만 출력합니다.

            JSON 스키마:
            {
              "contractType": "MONTHLY" | "JEONSE" | "SALE" | null,
              "priceMin": 정수(원) | null,        // 매매가 전용
              "priceMax": 정수(원) | null,
              "depositMin": 정수(원) | null,      // 보증금
              "depositMax": 정수(원) | null,
              "monthlyRentMin": 정수(원) | null,  // 월세
              "monthlyRentMax": 정수(원) | null,
              "roomNumber": 정수 | null,
              "excludeBanjiha": true | null,
              "floorMin": 정수 | null,
              "areaMin": 숫자(㎡) | null,
              "areaMax": 숫자(㎡) | null,
              "direction": "SOUTH" | "NORTH" | "EAST" | "WEST" | null,
              "sggName": "XX구" | null,
              "emdName": "XX동" | null,
              "campusMaxMinutes": 정수(분) | null,
              "semanticQuery": "필터로 표현하지 못한 나머지 의미"
            }

            금액 매핑 규칙 (가장 중요):
            - 매매가 → priceMin/priceMax. 매매 얘기일 때만 사용한다.
            - 월세 금액 → monthlyRentMin/monthlyRentMax. 절대 priceMax에 넣지 않는다.
            - 보증금·전세금 → depositMin/depositMax. 절대 priceMax에 넣지 않는다.
            - 금액은 반드시 원 단위 정수. 1만원=10000, 1억=100000000. 0의 개수를 검산할 것.
            - "이하/까지/안쪽" → Max, "이상/부터/넘는" → Min.
              "월세 60 이하" → monthlyRentMax: 600000
              "보증금 1000만원" → depositMax: 10000000 (기준 언급 없으면 Max로 해석)
              "보증금 1000, 월세 60 이하" → depositMax: 10000000, monthlyRentMax: 600000
              "전세 3억 이하" → contractType: "JEONSE", depositMax: 300000000
              "매매 5억대" → contractType: "SALE", priceMin: 500000000, priceMax: 600000000
            - 월세/보증금이 언급되면 contractType은 "MONTHLY"로 추론한다.

            기타 규칙:
            - "원룸"→roomNumber:1, "투룸"→2, "쓰리룸"→3
            - "반지하 제외/빼고/말고/싫어" → excludeBanjiha: true
            - "2층 이상" → floorMin: 2
            - "10평 이상" → areaMin: 33 (1평 = 3.3058㎡)
            - "남향" → direction: "SOUTH"
            - 언급되지 않은 필드는 null
            - 캠퍼스 인접: "시립대/서울시립대/학교/캠퍼스 + 도보 N분/근처/앞/인접" 표현이 있으면
              campusMaxMinutes에 명시된 분을 넣는다. 시간 언급 없이 "근처/앞"이면 20.
            - semanticQuery에는 이미 필터로 추출한 조건은 넣지 않는다.
              분위기·취향 표현(신축/채광/조용한/치안 등)만 남긴다. 아무것도 없으면 빈 문자열.

            예시:
            입력: "보증금 1000만원, 월세 60만원 이하 반지하는 싫어"
            출력: {"contractType":"MONTHLY","priceMin":null,"priceMax":null,
                   "depositMin":null,"depositMax":10000000,
                   "monthlyRentMin":null,"monthlyRentMax":600000,
                   "roomNumber":null,"excludeBanjiha":true,"floorMin":null,
                   "areaMin":null,"areaMax":null,"direction":null,
                   "sggName":null,"emdName":null,"campusMaxMinutes":null,
                   "semanticQuery":""}
            """;

    public HouseSearchFilter parse(String userQuery) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userQuery)
                    )
            );

            ChatResponse res = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);

            if (res == null || res.choices() == null || res.choices().isEmpty()) {
                log.warn("쿼리 파싱 응답 비어있음. 폴백 사용. query={}", userQuery);
                return HouseSearchFilter.fallback(userQuery);
            }

            String json = res.choices().get(0).message().content();
            return objectMapper.readValue(json, HouseSearchFilter.class);
        } catch (Exception e) {
            log.warn("쿼리 파싱 실패, 폴백 사용. query={}, error={}", userQuery, e.getMessage());
            return HouseSearchFilter.fallback(userQuery);
        }
    }

    record ChatResponse(List<Choice> choices) {
        record Choice(Message message) {
        }

        record Message(String content) {
        }
    }
}
