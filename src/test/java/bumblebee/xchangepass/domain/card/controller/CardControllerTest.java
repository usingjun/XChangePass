package bumblebee.xchangepass.domain.card.controller;

import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.card.dto.request.ChangeCardStatusRequest;
import bumblebee.xchangepass.domain.card.dto.response.BasicCardInfoResponse;
import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.global.security.jwt.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 단위 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestUserInitializer.class)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardService cardService;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class MockSecurityConfig {
        @Bean
        public UserDetailsService customUserDetailsService() {
            // username("1") 요청이 들어오면 CustomUserDetails를 반환하도록
            return username -> new CustomUserDetails(
                    1L,
                    username,
                    "",
                    "ROLE_USER"
            );
        }
    }

    @Test
    @WithUserDetails(value = "1", userDetailsServiceBeanName = "customUserDetailsService")
    void 실물카드발급_성공() throws Exception {
        doNothing().when(cardService).generatePhysicalCard(1L);

        mockMvc.perform(post("/api/v1/card/physical")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isCreated());

        verify(cardService).generatePhysicalCard(1L);
    }

    @Test
    @WithUserDetails(value = "1", userDetailsServiceBeanName = "customUserDetailsService")
    void 카드상태변경_성공() throws Exception {
        ChangeCardStatusRequest request = ChangeCardStatusRequest.builder()
                .cardType(CardType.PHYSICAL)
                .status(CardStatus.INACTIVE)
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        doNothing().when(cardService).changeCardStatus(1L, request);

        mockMvc.perform(put("/api/v1/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(cardService).changeCardStatus(1L, request);
    }

    @Test
    @WithUserDetails(value = "1", userDetailsServiceBeanName = "customUserDetailsService")
    void 보유카드목록조회_성공() throws Exception {
        BasicCardInfoResponse cardInfoResponse = BasicCardInfoResponse.builder()
                .cardId(1L)
                .cardStatus(CardStatus.ACTIVE)
                .cardType(CardType.PHYSICAL)
                .maskedCardNumber("1111-****-****-1234")
                .build();

        when(cardService.getBasicCardInfo(1L)).thenReturn(Collections.singletonList(cardInfoResponse));

        mockMvc.perform(get("/api/v1/card")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        verify(cardService).getBasicCardInfo(1L);
    }

    @Test
    @WithUserDetails(value = "1", userDetailsServiceBeanName = "customUserDetailsService")
    void 카드상세정보조회_성공() throws Exception {
        Long cardId = 1L;
        DetailedCardInfoResponse cardInfoResponse = DetailedCardInfoResponse.builder()
                .cardId(cardId)
                .cardType(CardType.PHYSICAL)
                .cardStatus(CardStatus.ACTIVE)
                .cardNumber("1111-1111-1111-1234")
                .cvc("123")
                .expirationDate(LocalDateTime.of(2023, 1, 30, 0, 0))
                .cardCreateDate(LocalDateTime.of(2023, 5, 1, 0, 0))
                .build();
        when(cardService.getDetailedCardInfo(cardId)).thenReturn(cardInfoResponse);

        mockMvc.perform(get("/api/v1/card/{cardId}", cardId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        verify(cardService).getDetailedCardInfo(cardId);
    }

    @TestConfiguration
    static class MockServiceConfig  {
        @Bean
        public CardService cardService() {
            return Mockito.mock(CardService.class);
        }
    }
}
