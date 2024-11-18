package com.scv.global.oauth2.handler;

import com.scv.global.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        log.error("CustomAccessDeniedHandler Error");
        ResponseUtil.sendResponse(response, HttpServletResponse.SC_FORBIDDEN, "CustomAccessDeniedHandler", "인가되지 않은 사용자입니다.");
    }
}
