package dacs.nguyenhuubang.bookingwebsiteV1.event.listener;
import dacs.nguyenhuubang.bookingwebsiteV1.entity.Booking;
import dacs.nguyenhuubang.bookingwebsiteV1.entity.SeatReservation;
import dacs.nguyenhuubang.bookingwebsiteV1.event.BookingCompleteEvent;
import dacs.nguyenhuubang.bookingwebsiteV1.service.BookingService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCompleteEventListener implements ApplicationListener<BookingCompleteEvent> {

    private final BookingService bookingService;
    private final JavaMailSender mailSender;
    private Booking theBooking;
    private String totalPrice;
    private String reservedSeats;
    private String numberOfTicket;
    private String ticketCode;
    private String roundTripId;

    @Override
    public void onApplicationEvent(BookingCompleteEvent event) {

        theBooking = event.getBooking();
        totalPrice = event.getTotalPrice();
        reservedSeats = event.getReservedSeatNames();
        numberOfTicket = event.getNumberofTicket();
        ticketCode = event.getTicketCode();
        roundTripId = event.getRoundTripId();
        String url = event.getApplicationUrl() + "&sentEmail=1";
        try {
            sendTicketCode(url);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String generateQRCodeUrl(String text) {
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=" + encodedText;
    }


    public void sendTicketCode(String url) throws Exception {

        String subject = "Đặt Vé Thành Công";
        String senderName = "Nhà xe Travelista";
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
        mailContentBuilder.append("<p>Cám ơn bạn đã chọn nhà xe của chúng tôi, dưới đây là thông tin vé của bạn</p>");
        mailContentBuilder.append("<p><strong>Thông tin vé lượt đi:</strong> tuyến: " + theBooking.getTrip().getRoute().getName() + ", ngày: " + theBooking.getBookingDate() + ", lúc: " + theBooking.getTrip().getStartTime() + "</p>");
        mailContentBuilder.append("<p><strong>Số lượng vé lượt đi:</strong> " + numberOfTicket + "</p>");
        mailContentBuilder.append("<p><strong>Chỗ ngồi lượt đi:</strong> " + reservedSeats + "</p>");
        mailContentBuilder.append("<p><strong>Mã vé lượt đi:</strong> " + ticketCode + "</p>");

        if (!roundTripId.isBlank()) {
            Booking booking = bookingService.get(Integer.valueOf(roundTripId));
            String send_reservedSeatNames = ""; // Chuỗi để lưu tên các ghế đã đặt
            for (SeatReservation seatReservation : booking.getSeatReservations()) {
                String seatName = seatReservation.getSeat().getName();
                send_reservedSeatNames += seatName + ", "; // Thêm tên của ghế vào chuỗi
            }
            send_reservedSeatNames = send_reservedSeatNames.substring(0, send_reservedSeatNames.length() - 2);
            mailContentBuilder.append("<p><strong>Thông tin vé lượt về:</strong> Tuyến" + booking.getTrip().getRoute().getName() + ", ngày" + booking.getBookingDate() + ", lúc" + booking.getTrip().getStartTime() + "</p>");
            mailContentBuilder.append("<p><strong>Số lượng vé lượt về:</strong> " + booking.getBookingDetails().get(0).getNumberOfTickets() + "</p>");
            mailContentBuilder.append("<p><strong>Chỗ ngồi lượt về:</strong> " + send_reservedSeatNames + "</p>");
            mailContentBuilder.append("<p><strong>Mã vé lượt về:</strong> " + booking.getBookingDetails().get(0).getId().getTicketCode() + "</p>");
        }

        mailContentBuilder.append("<p><strong>Trạng thái:</strong> Đã thanh toán</p>");

        Locale localeVN = new Locale("vi", "VN");
        NumberFormat vn = NumberFormat.getInstance(localeVN);
        String str2 = vn.format(Double.parseDouble(totalPrice));
        mailContentBuilder.append("<p><strong>Tổng tiền:</strong> " + str2 + "đ</p>");
        mailContentBuilder.append("<p>Quý khách vui lòng đến nhà xe trước thời gian khởi hành <strong>20 phút</strong>. Khi lên xe, quý khách vui lòng xuất trình mã QR cho nhân viên soát vé.<a href=\"" + url + "\"> Xem chi tiết vé ở đây</a></p>");
        // Tạo mã QR từ URL
        String qrCodeUrl = generateQRCodeUrl(url);
        mailContentBuilder.append("<img src=\"" + qrCodeUrl + "\" alt=\"QR Code\" width=\"200\" height=\"200\">");

        mailContentBuilder.append("</div>");
        mailContentBuilder.append("</body>");
        mailContentBuilder.append("</html>");

        String mailContent = mailContentBuilder.toString();
        MimeMessage message = mailSender.createMimeMessage();
        var messageHelper = new MimeMessageHelper(message);
        messageHelper.setFrom("nghbang1909@gmail.com", senderName);
        String sendEmail = "";
        if (!isValidEmail(theBooking.getUser().getEmail()))
            sendEmail=theBooking.getUser().getAddress();
        else sendEmail=theBooking.getUser().getEmail();
        System.out.println("Sending email to " + sendEmail + "...");
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

}
