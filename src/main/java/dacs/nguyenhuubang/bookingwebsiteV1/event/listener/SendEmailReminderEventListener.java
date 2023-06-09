package dacs.nguyenhuubang.bookingwebsiteV1.event.listener;


import dacs.nguyenhuubang.bookingwebsiteV1.entity.Booking;
import dacs.nguyenhuubang.bookingwebsiteV1.event.SendEmailReminderEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailReminderEventListener implements ApplicationListener<SendEmailReminderEvent> {

    private final JavaMailSender mailSender;
    private Booking theBooking;
    private String totalPrice;
    private String reservedSeats;
    private String numberOfTicket;
    private String ticketCode;
    private String url;

    public void sendTicketCode() throws MessagingException, UnsupportedEncodingException {
        String subject = "Nhắc Nhở Thanh Toán";
        String senderName = "Travelista";
        StringBuilder mailContentBuilder = new StringBuilder();
        mailContentBuilder.append("<html>");
        mailContentBuilder.append("<head>");
        mailContentBuilder.append("<style>");
        mailContentBuilder.append("body { font-family: Arial, sans-serif; }");
        mailContentBuilder.append("h3 { color: #383429; }");
        mailContentBuilder.append("h1 { color: #ffc31e; }");
        mailContentBuilder.append(".container { background-color: #ffffff;max-width: 500px;margin: 20px; padding: 20px; border: 3px solid #ffffff;border-radius: 10px;}");
        mailContentBuilder.append("p { margin-bottom: 10px; }");
        mailContentBuilder.append("a { color: #ffc31e; text-decoration: none;}");
        mailContentBuilder.append("</style>");
        mailContentBuilder.append("</head>");
        mailContentBuilder.append("<body style=\"background-color: #e1dada;padding:20px;border-radius: 10px;margin: 0 auto; max-width: fit-content;\">");
        mailContentBuilder.append("<div class=\"container\">");
        mailContentBuilder.append("<h1>TRAVELISTA</h1><hr>");
        mailContentBuilder.append("<h3>Xin chào, " + theBooking.getUser().getFullname() + "</h3>");
        mailContentBuilder.append("<p>Chúng tôi nhận thấy bạn đã đặt vé tại <a href=\"" + url + "\">Travelista</a></p>");
        mailContentBuilder.append("<p><strong>Theo tuyến: </strong> " + theBooking.getTrip().getRoute().getName() + ", ngày: " + theBooking.getBookingDate() + ", lúc: " + theBooking.getTrip().getStartTime() + "</p>");
        mailContentBuilder.append("<p><strong>Số lượng vé:</strong> " + numberOfTicket + "</p>");
        mailContentBuilder.append("<p><strong>Chỗ ngồi:</strong> " + reservedSeats + "</p>");
        mailContentBuilder.append("<p><strong>Mã vé:</strong> " + ticketCode + "</p>");
        mailContentBuilder.append("<p><strong>Trạng thái:</strong>Chưa thanh toán</p>");

        Locale localeVN = new Locale("vi", "VN");
        NumberFormat vn = NumberFormat.getInstance(localeVN);
        String str2 = vn.format(Double.parseDouble(totalPrice));
        mailContentBuilder.append("<p><strong>Tổng tiền:</strong> " + str2 + "đ</p>");
        mailContentBuilder.append("<p>Hóa đơn của bạn sẽ bị hủy nếu chưa thanh toán trước <strong>120 phút</strong> trước giờ khởi hành. Nếu bạn đã đặt vé mà chưa thanh toán, vui lòng <a href=\"" + url + "\">thanh toán</a> để nhận vé.</p>");
        mailContentBuilder.append("</div>");
        mailContentBuilder.append("</body>");
        mailContentBuilder.append("</html>");

        String mailContent = mailContentBuilder.toString();
        MimeMessage message = mailSender.createMimeMessage();
        var messageHelper = new MimeMessageHelper(message);
        messageHelper.setFrom("nghbang1909@gmail.com", senderName);
        String sendEmail = "";
        if (!isValidEmail(theBooking.getUser().getEmail())) {
            sendEmail = theBooking.getUser().getAddress();
            System.out.println("Sending not valid email to " + sendEmail + "...");
        } else {
            sendEmail = theBooking.getUser().getEmail();
            System.out.println("Sending email to " + sendEmail + "...");
        }

        messageHelper.setTo(sendEmail);
        messageHelper.setSubject(subject);
        messageHelper.setText(mailContent, true);
        mailSender.send(message);
    }

    public boolean isValidEmail(String email) {
        // Regex pattern để kiểm tra định dạng email
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    @Override
    public void onApplicationEvent(SendEmailReminderEvent event) {
        theBooking = event.getBooking();
        totalPrice = event.getTotalPrice();
        reservedSeats = event.getReservedSeatNames();
        numberOfTicket = event.getNumberofTicket();
        ticketCode = event.getTicketCode();
        url = event.getApplicationUrl() + "/users/tickets/basket";

        try {
            sendTicketCode();
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
