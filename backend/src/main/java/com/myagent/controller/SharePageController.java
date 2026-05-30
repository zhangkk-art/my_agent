package com.myagent.controller;

import com.myagent.model.Conversation;
import com.myagent.model.Message;
import com.myagent.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SharePageController {

    private final ConversationService conversationService;

    public SharePageController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping(value = "/share/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public String sharePage(@PathVariable String token) {
        Conversation conv;
        try {
            conv = conversationService.getSharedConversation(token);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return notFoundPage();
            }
            throw e;
        }
        return buildSharePage(conv);
    }

    private String buildSharePage(Conversation conv) {
        StringBuilder msgs = new StringBuilder();
        if (conv.getMessages() != null) {
            for (Message m : conv.getMessages()) {
                String role = "user".equals(m.getRole()) ? "You" : "AI";
                String side = "user".equals(m.getRole()) ? "msg-user" : "msg-ai";
                String text = escapeHtml(m.getContent());
                msgs.append("""
                    <div class="msg-row %s">
                      <div class="msg-bubble">
                        <div class="msg-role">%s</div>
                        <div class="msg-text">%s</div>
                      </div>
                    </div>
                    """.formatted(side, role, text));
            }
        }

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>%s - Shared from Ayer</title>
            <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#1a1a2e;color:#e2e8f0;min-height:100vh}
            .page{max-width:800px;margin:0 auto;padding:0 24px}
            header{position:sticky;top:0;background:#1a1a2e;border-bottom:1px solid #2a3a5c;padding:14px 0;z-index:10;display:flex;align-items:center;gap:16px}
            .logo{display:flex;align-items:center;gap:6px;font-weight:600;font-size:14px;color:#60a5fa;flex-shrink:0;text-decoration:none}
            .logo svg{width:20px;height:20px}
            .title{font-size:15px;font-weight:500;color:#a0aec0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .msgs{padding:24px 0;display:flex;flex-direction:column;gap:16px}
            .msg-row{display:flex}
            .msg-row.msg-user{justify-content:flex-end}
            .msg-bubble{max-width:75%%;padding:12px 16px;border-radius:12px;font-size:14px}
            .msg-user .msg-bubble{background:#253553;border-bottom-right-radius:4px}
            .msg-ai .msg-bubble{border-bottom-left-radius:4px}
            .msg-role{font-size:11px;font-weight:600;color:#718096;text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px}
            .msg-text{line-height:1.7;word-break:break-word;white-space:pre-wrap}
            footer{text-align:center;padding:32px 0;border-top:1px solid #2a3a5c;color:#718096;font-size:12px}
            @media(max-width:768px){.page{padding:0 12px}.msg-bubble{max-width:88%%}}
            </style>
            </head>
            <body>
            <div class="page">
            <header>
            <a class="logo" href="/">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M10 2 L11.2 8.8 L18 10 L11.2 11.2 L10 18 L8.8 11.2 L2 10 L8.8 8.8 Z"/><path d="M18.5 13 L19.2 16.3 L22.5 17 L19.2 17.7 L18.5 21 L17.8 17.7 L14.5 17 L17.8 16.3 Z"/></svg>
            Ayer
            </a>
            <h1 class="title">%s</h1>
            </header>
            <div class="msgs">%s</div>
            <footer>Shared via Ayer — <a href="/" style="color:#60a5fa">Start your own conversation</a></footer>
            </div>
            </body>
            </html>
            """.formatted(escapeHtml(conv.getTitle()), escapeHtml(conv.getTitle()), msgs.toString());
    }

    private String notFoundPage() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Not Found - Ayer</title>
            <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#1a1a2e;color:#e2e8f0;display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center}
            .nf svg{color:#718096;margin-bottom:16px}
            .nf h2{font-size:20px;margin-bottom:8px}
            .nf p{font-size:14px;color:#718096}
            </style></head>
            <body>
            <div class="nf">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            <h2>Not Found</h2><p>This shared conversation doesn't exist or has been revoked.</p>
            </div></body></html>""";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
