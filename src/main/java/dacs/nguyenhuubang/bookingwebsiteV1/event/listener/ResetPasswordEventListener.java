package dacs.nguyenhuubang.bookingwebsiteV1.event.listener;

import dacs.nguyenhuubang.bookingwebsiteV1.entity.UserEntity;
import dacs.nguyenhuubang.bookingwebsiteV1.event.ResetPasswordEvent;
import dacs.nguyenhuubang.bookingwebsiteV1.service.UserService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResetPasswordEventListener implements ApplicationListener<ResetPasswordEvent> {

    private final UserService userService;
    private final JavaMailSender mailSender;
    private UserEntity user;
    private String email;

    @Override
    public void onApplicationEvent(ResetPasswordEvent event) {
        email = event.getUser().getEmail();
        user = event.getUser();
        String token = UUID.randomUUID().toString();
        userService.updateResetPassword(token, user);
        String resetUrl = event.getApplicationUrl() + "/reset-password?token=" + token;
        String url = event.getApplicationUrl();
        try {
            sendVerificationEmail(url, resetUrl);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

/*    public void sendVerificationEmail(String url, String resetUrl) throws MessagingException, UnsupportedEncodingException {
        String subject = "Khôi Phục Mật Khẩu";
        String senderName = "Nhà xe Travelista";
        String mailContent = "<html><head><style>" +
                "body { font-family: Arial, sans-serif; }" +
                "h2 { color: #325174; }" +
                "p { font-size: 16px; }" +
                "a { background-color: #bbbfcb; color: #FFFFFF; padding: 10px 15px; border-radius: 5px; text-decoration: none; }" +
                "</style></head>" +
                "<body>" +
                "<h2>Xin chào bạn</h2>" +
                "<p>Chúng tôi nhận thấy rằng bạn đã yêu cầu khôi phục mật khẩu tại <a href=\"" + url + "\">Travelista</a>Vui lòng ấn vào đường dẫn bên dưới để khôi phục mật khẩu của bạn.</p>" +
                "<p>Hãy <a href=\"" + resetUrl + "\">khôi phục mật khẩu</a> và vui lòng không chia sẻ đường dẫn này cho bất cứ ai.</p>" +
                "<p>Xin cám ơn,<br>Nhà xe Travelista</p>" +
                "</body></html>";
        MimeMessage message = mailSender.createMimeMessage();
        var messageHelper = new MimeMessageHelper(message);
        messageHelper.setFrom("nghbang1909@gmail.com", senderName);
        System.out.println("Sending email to " + email + "...");
        messageHelper.setTo(email);
        messageHelper.setSubject(subject);
        messageHelper.setText(mailContent, true);
        mailSender.send(message);
    }*/

    public void sendVerificationEmail(String url, String resetUrl) throws MessagingException, UnsupportedEncodingException {
        String subject = "Khôi Phục Mật Khẩu";
        String senderName = "Travelista";
        StringBuilder mailContentBuilder = new StringBuilder();
        mailContentBuilder.append("<html>");
        mailContentBuilder.append("<head>");
        mailContentBuilder.append("<style>");
        mailContentBuilder.append("body { font-family: Arial, sans-serif; }");
        mailContentBuilder.append("h3 { color: #383429; }");
        mailContentBuilder.append("h1 { color: #ffc31e; }");
        mailContentBuilder.append(".container { background-color: #ffffff;max-width: 400px;margin: 20px; padding: 20px; border: 3px solid #ffffff; border-radius: 10px;}");
        mailContentBuilder.append("p { margin-bottom: 10px; }");
        mailContentBuilder.append("a { color: #ffc31e;text-decoration: none; }");
        mailContentBuilder.append("</style>");
        mailContentBuilder.append("</head>");
        mailContentBuilder.append("<body style=\"max-width: fit-content;margin: 0 auto;background-color: #e1dada;padding:20px;border-radius: 10px;\">");
        mailContentBuilder.append("<div class=\"container\">");
        mailContentBuilder.append("<h1>TRAVELISTA</h1><hr>");
        mailContentBuilder.append("<h3>Xin chào bạn.</h3>");
        mailContentBuilder.append("<p>Chúng tôi nhận thấy rằng bạn đã gửi yêu cầu khôi phục mật khẩu tại <a href=\"" + url + "\">Travelista</a>. Vui lòng ấn vào đường dẫn để khôi phục mật khẩu của bạn.</p>");
        mailContentBuilder.append("<p>Hãy <a href=\"" + resetUrl + "\">khôi phục mật khẩu</a> và vui lòng không chia sẻ đường dẫn này cho bất cứ ai.</p>");
        mailContentBuilder.append("<p>Xin cám ơn,<br>Nhà xe Travelista</p>");
        mailContentBuilder.append("</div>");
        mailContentBuilder.append("</body>");
        mailContentBuilder.append("</html>");

        String mailContent = mailContentBuilder.toString();
        MimeMessage message = mailSender.createMimeMessage();
        var messageHelper = new MimeMessageHelper(message);
        messageHelper.setFrom("nghbang1909@gmail.com", senderName);
        System.out.println("Sending email to " + email + "...");
        messageHelper.setTo(email);
        messageHelper.setSubject(subject);
        messageHelper.setText(mailContent, true);
        mailSender.send(message);
    }

}
