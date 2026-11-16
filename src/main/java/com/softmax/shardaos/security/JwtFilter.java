package com.softmax.shardaos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * ✅ SABSE ZAROORI FIX:
     * - External APIs ko JWT filter se bypass karo
     * - In paths ke liye filter chalega hi nahi
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/external/")
                || path.startsWith("/api/auth/signin")
                || path.startsWith("/api/auth/logout")
                || path.startsWith("/api/test");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        System.out.println("🔥 JWT FILTER HIT: " + path);

        // ✅ Safety net — agar shouldNotFilter() miss ho jaye
        if (path.startsWith("/api/v1/external/")
                || path.equals("/api/auth/signin")
                || path.equals("/api/auth/logout")
                || path.startsWith("/api/test")) {
            System.out.println("⏭️  Skipping JWT filter for: " + path);
            filterChain.doFilter(request, response);
            return;
        }

        // 🔥 Extract token from Cookie OR Header
        String token = extractTokenFromRequest(request);

        if (token == null) {
            System.out.println("❌ Missing Token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing Token");
            return;
        }

        try {
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.extractUserId(token);
                request.setAttribute("userId", userId);
                System.out.println("✅ Token Valid: " + userId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                System.out.println("✅ Authentication set for user: " + userId);

            } else {
                System.out.println("❌ Invalid Token");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid Token");
                return;
            }
        } catch (Exception e) {
            System.out.println("❌ Token Error");
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token Error");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("auth_token".equals(cookie.getName())) {
                    System.out.println("✅ Token found in Cookie");
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            System.out.println("✅ Token found in Authorization Header");
            return header.substring(7);
        }
        return null;
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String token = extractTokenFromCookie(request);
        if (token != null) return token;
        return extractTokenFromHeader(request);
    }
}









//package com.softmax.shardaos.security;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.Cookie;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.ArrayList;
//
//@Component
//public class JwtFilter extends OncePerRequestFilter {
//
//    @Autowired
//    private JwtUtil jwtUtil;
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//            throws ServletException, IOException {
//
//        String path = request.getRequestURI();
//        System.out.println("🔥 JWT FILTER HIT: " + path);
//
//        // ✅ SIRF signin aur logout skip karo - /me pe filter lagega
//        if (path.equals("/api/auth/signin") || path.equals("/api/auth/logout") || path.startsWith("/api/test")) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        // 🔥 Extract token from Cookie OR Header
//        String token = extractTokenFromRequest(request);
//
//        if (token == null) {
//            System.out.println("❌ Missing Token");
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            response.getWriter().write("Missing Token");
//            return;
//        }
//
//        try {
//            if (jwtUtil.validateToken(token)) {
//                Long userId = jwtUtil.extractUserId(token);
//                request.setAttribute("userId", userId);
//                System.out.println("✅ Token Valid: " + userId);
//
//                UsernamePasswordAuthenticationToken authentication =
//                        new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
//                SecurityContextHolder.getContext().setAuthentication(authentication);
//                System.out.println("✅ Authentication set for user: " + userId);
//
//            } else {
//                System.out.println("❌ Invalid Token");
//                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                response.getWriter().write("Invalid Token");
//                return;
//            }
//        } catch (Exception e) {
//            System.out.println("❌ Token Error");
//            e.printStackTrace();
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            response.getWriter().write("Token Error");
//            return;
//        }
//
//        filterChain.doFilter(request, response);
//    }
//
//    private String extractTokenFromCookie(HttpServletRequest request) {
//        if (request.getCookies() != null) {
//            for (Cookie cookie : request.getCookies()) {
//                if ("auth_token".equals(cookie.getName())) {
//                    System.out.println("✅ Token found in Cookie");
//                    return cookie.getValue();
//                }
//            }
//        }
//        return null;
//    }
//
//    private String extractTokenFromHeader(HttpServletRequest request) {
//        String header = request.getHeader("Authorization");
//        if (header != null && header.startsWith("Bearer ")) {
//            System.out.println("✅ Token found in Authorization Header");
//            return header.substring(7);
//        }
//        return null;
//    }
//
//    private String extractTokenFromRequest(HttpServletRequest request) {
//        String token = extractTokenFromCookie(request);
//        if (token != null) return token;
//        return extractTokenFromHeader(request);
//    }
//}