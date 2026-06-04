package foodiediary.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final String secretKey = "yourSuperSecretKey12345678901234567890"; // 💡 실제로는 환경변수로 관리!

    // 필터 제외할 경로들
    private static final List<String> excludePaths = List.of(
            "/foodiediary/user/login",
            "/foodiediary/user/signup"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 로그인, 회원가입, 업로드 정적 파일은 토큰 검증 없이 통과
        if (excludePaths.contains(uri) || uri.startsWith("/uploads")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7); // "Bearer " 제거

        try {
            // 토큰 검증
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))  // 꼭 getBytes() 써야 함!
                    .build()
                    .parseClaimsJws(token)
                    .getBody();


            // 필요한 사용자 정보를 request에 심을 수도 있음
            request.setAttribute("id", claims.getSubject());

            // 다음 필터 또는 컨트롤러로 전달
            filterChain.doFilter(request, response);

        } catch (JwtException e) {
            // 토큰이 유효하지 않으면 401 응답
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}

