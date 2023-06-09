package dacs.nguyenhuubang.bookingwebsiteV1.controller.user;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import dacs.nguyenhuubang.bookingwebsiteV1.config.momo.MoMoSecurity;
import dacs.nguyenhuubang.bookingwebsiteV1.config.momo.PaymentRequest;
import dacs.nguyenhuubang.bookingwebsiteV1.config.vnpay.Config;
import dacs.nguyenhuubang.bookingwebsiteV1.entity.*;
import dacs.nguyenhuubang.bookingwebsiteV1.event.BookingCompleteEvent;
import dacs.nguyenhuubang.bookingwebsiteV1.exception.ResourceNotFoundException;
import dacs.nguyenhuubang.bookingwebsiteV1.exception.SeatHasBeenReseredException;
import dacs.nguyenhuubang.bookingwebsiteV1.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@RequestMapping("/users")
@Controller
public class UsersBookingController {
    private final UserService userService;
    private final TripService tripService;
    private final SeatService seatService;
    private final BookingService bookingService;
    private final BookingDetailsService bookingDetailsService;
    private final SeatReservationService seatReservationService;
    private final ApplicationEventPublisher publisher;
    public static final String ACCOUNT_SID = "ACa3f5ab465b8859f75c2294541894d897";
    public static final String AUTH_TOKEN = "f842a3b72331083b7d0d5072e6841d69";
    public static final String TWILIO_PHONE_NUMBER = "+13156303801";


    public boolean isValidEmail(String email) {
        // Regex pattern để kiểm tra định dạng email
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    @PostMapping("/booking-trip")
    @Transactional(rollbackFor = {Exception.class, Throwable.class, SeatHasBeenReseredException.class})
    public String bookRoundTrip(Principal p, Model model, @RequestParam(value = "bookedId", required = false) Integer bookedId, @RequestParam("startTime") LocalDate startTime, @RequestParam("selectedTripId") Integer selectedTripId,
                                @RequestParam("inputSelectedSeats") String inputSelectedSeats, RedirectAttributes re, @RequestParam(value = "endTime", required = false) LocalDate endTime) {
        String validEmail = p.getName();
        Optional<UserEntity> foundUser = userService.findbyEmail(validEmail);
        UserEntity checkUser = null;
        if (foundUser != null) {
            checkUser = foundUser.get();
        } else {
            re.addFlashAttribute("errorMessage", "Lỗi tìm kiếm người dùng!");
            return "redirect:/home";
        }
        if (!isValidEmail(validEmail) && checkUser.getAddress() == null) {
            model.addAttribute("user", checkUser);
            model.addAttribute("header", "Cung cấp email");
            model.addAttribute("currentPage", "Chỉnh sửa tài khoản");
            model.addAttribute("title", "Để hoàn thành việc đặt vé và nhận vé, quý khách vui lòng cung cấp địa chỉ email.");
            model.addAttribute("gbUserName", checkUser.getEmail());
            return "pages/fill_out_email";

        }
        if (!isValidEmail(validEmail) && !isValidEmail(checkUser.getAddress())) {
            model.addAttribute("user", checkUser);
            model.addAttribute("gbUserName", checkUser.getEmail());
            return "pages/fill_out_email";
        }

        if (endTime != null) {
            List<Long> seatIds = new ArrayList<>();
            String[] seatIdArray = inputSelectedSeats.split(",");
            for (String seatId : seatIdArray) {
                seatIds.add(Long.valueOf(seatId));
            }
            LocalTime now = LocalTime.now();
            LocalDate today = LocalDate.now();
            Trip trip = tripService.get(selectedTripId);

            if (startTime.isEqual(today) && trip.getStartTime().compareTo(now) <= 0) {
                re.addFlashAttribute("errorMessage", "Chuyến này đã xuất phát rồi!");
                return "redirect:/home";
            }

            List<Seat> seatsReserved = new ArrayList<>();
            for (Long seatId : seatIds) {
                Seat seat = seatService.get(seatId);
                seatsReserved.add(seat);
            }
            try {
                //Save booking
                Trip bookingTrip = trip;
                Booking booking = new Booking();
                UserEntity user = checkUser;

                booking.setTrip(bookingTrip);
                booking.setBookingDate(startTime);
                booking.setIsPaid(false);
                booking.setUser(user);

                Booking savedBooking = bookingService.save(booking);

                //Save booking details
                BookingDetails bookingDetails = new BookingDetails();
                BookingDetailsId bookingDetailsId = new BookingDetailsId();
                bookingDetailsId.setBookingId(savedBooking.getId());

                bookingDetails.setId(bookingDetailsId);
                bookingDetails.setNumberOfTickets(seatsReserved.size());
                BookingDetails savedBookingDetails = bookingDetailsService.save(bookingDetails, " ");

                List<BookingDetails> list = new ArrayList<>();
                list.add(savedBookingDetails);
                savedBooking.setBookingDetails(list);
                bookingService.save(savedBooking);
                //Save seat reservation
                Integer tempId = null;
                if (seatsReserved.size() == 1) {
                    SeatReservation seatReservation = new SeatReservation();
                    seatReservation.setBooking(savedBooking);
                    seatReservation.setSeat(seatsReserved.get(0));
                    seatReservationService.save(seatReservation, tempId);
                } else {
                    for (Seat seat : seatsReserved) {
                        SeatReservation seatReservation = new SeatReservation();
                        seatReservation.setBooking(savedBooking);
                        seatReservation.setSeat(seat);
                        seatReservationService.save(seatReservation, tempId);
                    }
                }

                List<Trip> foundTrips = tripService.findTripsByCitiesAndStartTime(trip.getRoute().getEndCity(), trip.getRoute().getStartCity());
                foundTrips.sort(Comparator.comparing(Trip::getStartTime));
                Map<Integer, Integer> availableSeatsMap = new HashMap<>();
                Map<Integer, List<Seat>> loadAvailableSeatsMap = new HashMap<>();
                Map<Integer, List<Seat>> loadReservedSeat = new HashMap<>();
                for (Trip trip2 : foundTrips) {
                    int totalSeat = trip2.getVehicle().getCapacity();
                    int seatReserved = seatReservationService.checkAvailableSeat(trip2, endTime);
                    List<Seat> seatsAvailable = seatReservationService.listAvailableSeat(trip2.getVehicle(), trip2, endTime);
                    List<Seat> listReservedSeat = seatReservationService.listReservedSeat(trip2.getVehicle(), trip2, startTime);
                    int availableSeats = totalSeat - seatReserved;

                    loadAvailableSeatsMap.put(trip2.getId(), seatsAvailable);
                    loadReservedSeat.put(trip2.getId(), listReservedSeat);
                    availableSeatsMap.put(trip2.getId(), availableSeats);
                }

                model.addAttribute("foundTrips", foundTrips);
                model.addAttribute("bookedId", savedBooking.getId());
                model.addAttribute("loadAvailableSeatsMap", loadAvailableSeatsMap);
                model.addAttribute("listReservedSeat", loadReservedSeat);
                model.addAttribute("availableSeatsMap", availableSeatsMap);
                model.addAttribute("header", "Tìm chuyến về");
                model.addAttribute("currentPage", "Tìm chuyến");
                model.addAttribute("startCity", trip.getRoute().getEndCity().getName());
                model.addAttribute("endCity", trip.getRoute().getStartCity().getName());
                model.addAttribute("startTime", endTime);
                return "pages/find_trip";
            } catch (Exception e) {
                re.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/home";
            }
        } else {
            return bookTrip(bookedId, model, startTime, selectedTripId, inputSelectedSeats, re);
        }
    }

    @PostMapping("/book")
    public String bookTrip(Integer bookedId, Model model, @RequestParam("startTime") LocalDate startTime, @RequestParam("selectedTripId") Integer selectedTripId,
                           @RequestParam("inputSelectedSeats") String inputSelectedSeats, RedirectAttributes re) {
        List<Long> seatIds = new ArrayList<>();
        String[] seatIdArray = inputSelectedSeats.split(",");
        for (String seatId : seatIdArray) {
            seatIds.add(Long.valueOf(seatId));
        }
        if (seatIds.isEmpty()) {
            re.addFlashAttribute("errorMessage", "Không tìm thấy ghế ngồi");
            if (bookedId != null)
                bookingService.delete(bookedId);
            return "redirect:/home";
        }
        try {
            LocalTime now = LocalTime.now();
            LocalDate today = LocalDate.now();
            Trip trip = tripService.get(selectedTripId);
            if (today.isEqual(startTime) && trip.getStartTime().compareTo(now) <= 0) {
                re.addFlashAttribute("errorMessage", "Chuyến này đã xuất phát rồi!");
                if (bookedId != null)
                    bookingService.delete(bookedId);
                return "redirect:/home";
            }
            List<Seat> seatsReserved = new ArrayList<>();
            for (Long seatId : seatIds) {
                Seat seat = seatService.get(seatId);
                seatsReserved.add(seat);
            }


            if (bookedId != null) {
                Booking roundTrip = bookingService.get(bookedId);
                model.addAttribute("roundTrip", roundTrip);
                model.addAttribute("hasRoundTrip", true);
            }


            if (!seatsReserved.isEmpty()) {
                model.addAttribute("trip", trip);
                model.addAttribute("bookedId", bookedId);
                model.addAttribute("seatsReserved", seatsReserved);
                model.addAttribute("startTime", startTime);
                model.addAttribute("header", "Xác nhận chuyến đi");
                model.addAttribute("currentPage", "xác nhận");

                if (trip.getRoute().getStartCity().getName().trim().equals("Sài Gòn")) {
                    model.addAttribute("storeAddress", "Trường Đại học Hutech - KCN Cao");
                }
                if (trip.getRoute().getStartCity().getName().trim().equals("Tây Ninh")) {
                    model.addAttribute("storeAddress", "Bến xe Tây Ninh");
                }
                if (trip.getRoute().getStartCity().getName().trim().equals("Vũng Tàu")) {
                    model.addAttribute("storeAddress", "Bến xe Vũng Tàu");
                }

                return "pages/confirm_booking";
            } else {
                re.addFlashAttribute("errorMessage", "Không tìm thấy ghế ngồi");
                if (bookedId != null)
                    bookingService.delete(bookedId);
                return "redirect:/home";
            }
        } catch (ResourceNotFoundException e) {
            re.addFlashAttribute("errorMessage", "Không tìm thấy ghế ngồi");
            if (bookedId != null)
                bookingService.delete(bookedId);
            return "redirect:/home";
        }
    }

    @PostMapping("/save")
    @Transactional(rollbackFor = {Exception.class, Throwable.class, SeatHasBeenReseredException.class})
    public String saveBooking(@RequestParam(value = "note", required = false) String note, @RequestParam(value = "address", required = false) String userAddress, @RequestParam(value = "noteRoundTrip", required = false) String noteRoundTrip, @RequestParam(value = "bookedId", required = false) Integer bookedId, Model model, @ModelAttribute("trip") Trip trip, @RequestParam("date") @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate date, RedirectAttributes re, @RequestParam("seatsReserved") List<Integer> seatIds, final HttpServletRequest request) throws Exception {

        List<Seat> seatsReserved = new ArrayList<>();
        for (Integer seatId : seatIds) {
            Seat seat = seatService.get(Long.valueOf(seatId));
            seatsReserved.add(seat);
        }
        if (seatsReserved.isEmpty()) {
            re.addFlashAttribute("errorMessage", "Không tìm thấy ghế ngồi");
            if (bookedId != null)
                bookingService.delete(bookedId);
            return "redirect:/home";
        }

        //Save booking
        Trip bookingTrip = trip;
        Booking booking = new Booking();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserEntity user = userService.findbyEmail(email).get();

        booking.setTrip(bookingTrip);
        booking.setBookingDate(date);
        booking.setIsPaid(false);
        booking.setUser(user);
        if (note.isBlank()) {
            booking.setNote(null);
        } else booking.setNote(note);
        if (userAddress.isBlank()) {
            booking.setUserAddress(null);
        } else booking.setUserAddress(userAddress);
        Booking savedBooking = bookingService.save(booking);

        //Save booking details
        BookingDetails bookingDetails = new BookingDetails();
        BookingDetailsId bookingDetailsId = new BookingDetailsId();
        bookingDetailsId.setBookingId(savedBooking.getId());

        bookingDetails.setId(bookingDetailsId);
        bookingDetails.setNumberOfTickets(seatsReserved.size());
        BookingDetails savedBookingDetails = bookingDetailsService.save(bookingDetails, " ");

        List<BookingDetails> list = new ArrayList<>();
        list.add(savedBookingDetails);
        savedBooking.setBookingDetails(list);
        bookingService.save(savedBooking);
        try {
            //Save seat reservation
            Integer tempId = null;
            if (seatsReserved.size() == 1) {
                SeatReservation seatReservation = new SeatReservation();
                seatReservation.setBooking(savedBooking);
                seatReservation.setSeat(seatsReserved.get(0));
                seatReservationService.save(seatReservation, tempId);
            } else {
                for (Seat seat : seatsReserved) {
                    SeatReservation seatReservation = new SeatReservation();
                    seatReservation.setBooking(savedBooking);
                    seatReservation.setSeat(seat);
                    seatReservationService.save(seatReservation, tempId);
                }
            }
        } catch (SeatHasBeenReseredException e) {
            model.addAttribute("message", "Ghế này đã được đặt rồi!");
            model.addAttribute("header", "Xảy ra lỗi");
            model.addAttribute("currentPage", "Lỗi");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return "error_message";
        }

        //Thanh toán
        Float roundTripPrice = 0.0F;
        String roundTripId = "";
        if (bookedId != null) {
            try {
                Booking roundTrip = bookingService.get(bookedId);
                if (noteRoundTrip.isBlank()) {
                    roundTrip.setNote(null);
                } else roundTrip.setNote(noteRoundTrip);
                bookingService.save(roundTrip);
                roundTripId = String.valueOf(bookedId);
                roundTripPrice = roundTrip.getBookingDetails().get(0).getTotalPrice();
            } catch (Exception e) {
                model.addAttribute("message", e.getMessage());
                model.addAttribute("header", "Xảy ra lỗi");
                model.addAttribute("currentPage", "Lỗi");
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return "error_message";
            }
        }

        String bookingId = String.valueOf(savedBooking.getId()) + "_" + roundTripId;//nối chuỗi id gửi mail
        // lấy url thanh toán VNPAY

        long vnpay_Amount = (long) ((savedBookingDetails.getTotalPrice() + roundTripPrice) * 100);
        String vnpayPaymentUrl = paymentVnpay(vnpay_Amount, bookingId, request);
        //Lấy url thanh toán Momo
        String momoAmount = String.valueOf(savedBookingDetails.getTotalPrice() + roundTripPrice);
        String sub_momoAmount = momoAmount.substring(0, momoAmount.length() - 2);

        String momoPaymentUrl = paymentMomo(sub_momoAmount, bookingId, request);
        model.addAttribute("momo", momoPaymentUrl);

        model.addAttribute("vnpay", vnpayPaymentUrl);
        model.addAttribute("startTime", savedBooking.getBookingDate());
        model.addAttribute("currentPage", "thanh toán");
        return "pages/payment_methods";
    }

    @GetMapping("/vnpay-payment-result")
    public String showResult(Model model, @RequestParam("vnp_Amount") String amount,
                             @RequestParam("vnp_OrderInfo") String bookingId,
                             @RequestParam("vnp_ResponseCode") String responseCode,
                             final HttpServletRequest request,
                             @RequestParam(value = "sentEmail", required = false) String sentEmail, Principal p
    ) {
        if (responseCode.equals("00")) {
            String[] parts = bookingId.split("_");
            String part1 = parts[0];
            String part2 = parts.length > 1 ? parts[1] : "";
            Booking booking = bookingService.get(Integer.parseInt(part1));
            booking.setIsPaid(true);
            Booking myBooking = bookingService.save(booking);
            List<Seat> reservedSeat = seatReservationService.getReservedSeat(myBooking);
            String totalPrice = amount.substring(0, amount.length() - 2);

            if (!part2.isBlank()) {
                Booking booking2 = bookingService.get(Integer.parseInt(part2));
                booking2.setIsPaid(true);
                Booking myBooking2 = bookingService.save(booking2);
                List<Seat> reservedSeat2 = seatReservationService.getReservedSeat(myBooking2);
                model.addAttribute("myBooking2", myBooking2);
                model.addAttribute("seatsReserved2", reservedSeat2);
                model.addAttribute("hasRoundTrip", true);
            }

            if (sentEmail == null) {
                System.out.println("Sending email...");
                sendEmail(part2, request, totalPrice, myBooking, reservedSeat);
                model.addAttribute("bookingTransit", true);
                //Gửi sms
                UserEntity checkUser = userService.findbyEmail(p.getName()).get();
                String destinyPhone = checkUser.getAddress();
                if (destinyPhone != null) {
                    if (validatePhoneNumber(destinyPhone)) {
                        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
                        String message = "Cám ơn bạn đã đặt vé tại Travelista, phương thức thanh toán VNPAY. Tuyến: " + myBooking.getTrip().getRoute().getName() + ", lúc: " + myBooking.getTrip().getStartTime() + ", mã vé: " + myBooking.getBookingDetails().get(0).getId().getTicketCode() + ". Vui lòng mang theo thông tin email hoặc tin nhắn này để soát vé trước khi lên xe, hãy kiểm tra email của bạn để xem chi tiết vé";
                        // Sử dụng thư viện Twilio để gửi tin nhắn SMS
                        System.out.println("Sending sms...");
                        Message.creator(
                                new PhoneNumber(destinyPhone), // Số điện thoại người nhận (+84 + số điện thoại)
                                new PhoneNumber(TWILIO_PHONE_NUMBER), // Số điện thoại nguồn (Twilio phone number)
                                message// Nội dung tin nhắn
                        ).create();
                    }
                }
            } else model.addAttribute("bookingTransit", false);
            String paymentMethod = "Thanh toán Vnpay ID: " + part1;
            model.addAttribute("paymentMethod", paymentMethod);
            model.addAttribute("totalPrice", totalPrice);
            model.addAttribute("myBooking", myBooking);
            model.addAttribute("seatsReserved", reservedSeat);
            model.addAttribute("currentPage", "Hóa đơn");
            model.addAttribute("header", "Đặt vé thành công.");
            return "pages/payment_result";

        } else {
            model.addAttribute("message", "Xảy ra lỗi trong quá trình thanh toán");
            model.addAttribute("header", "Xảy ra lỗi");
            model.addAttribute("currentPage", "Lỗi");
            return "error_message";
        }

    }

    private String applicationUrl(HttpServletRequest request) {
        return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    }


    @GetMapping("/payment-basket")
    private String basketPayment(@RequestParam("bookingId") int bookingId, @RequestParam("totalPrice") double totalPrice, Model model, final HttpServletRequest request) throws Exception {

        long vnpay_Amount = (long) (totalPrice * 100);
        String vnpayPaymentUrl = paymentVnpay(vnpay_Amount, String.valueOf(bookingId), request);

        //Lấy url thanh toán Momo
        String momoAmount = String.valueOf(totalPrice);
        String sub_momoAmount = momoAmount.substring(0, momoAmount.length() - 2);

        String momoPaymentUrl = paymentMomo(sub_momoAmount, String.valueOf(bookingId), request);
        model.addAttribute("momo", momoPaymentUrl);

        model.addAttribute("vnpay", vnpayPaymentUrl);
        model.addAttribute("currentPage", "Phương thức thanh toán");
        return "pages/payment_methods";
    }

    private String paymentVnpay(long send_amount, String bookingId, HttpServletRequest request) throws UnsupportedEncodingException {
        //Thanh toán VNPAY
        long amount = send_amount;
        String vnp_TxnRef = Config.getRandomNumber(8);
        String vnp_TmnCode = Config.vnp_TmnCode;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", Config.vnp_Version);
        vnp_Params.put("vnp_Command", Config.vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        String vnp_IpAddr = Config.getIpAddress(request);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
        // vnp_Params.put("vnp_BankCode", "NCB");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", bookingId);
        vnp_Params.put("vnp_OrderType", "billpayment");
        vnp_Params.put("vnp_Locale", "vn");

        String vnp_Returnurl = applicationUrl(request) + "/users/vnpay-payment-result";
        vnp_Params.put("vnp_ReturnUrl", vnp_Returnurl);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                //Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                //Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = Config.hmacSHA512(Config.vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = Config.vnp_PayUrl + "?" + queryUrl;
        System.out.println("Called: " + paymentUrl);
        return paymentUrl;
    }


    public String paymentMomo(String send_amount, String bookingId, HttpServletRequest request) throws Exception {

        // Request params needed to request MoMo system
        String endpoint = "https://test-payment.momo.vn/gw_payment/transactionProcessor";
        String partnerCode = "MOMOOJOI20210710";
        String accessKey = "iPXneGmrJH0G8FOP";
        String serectkey = "sFcbSGRSJjwGxwhhcEktCHWYUuTuPNDB";

/*        String endpoint ="https://test-payment.momo.vn/v2/gateway/api/create";
        String partnerCode = "MOMOBKUN20180529";
        String accessKey = "klm05TvNBzhg7h7j";
        String serectkey = "at67qH6mk8w5Y1nAyMoYKMWACiEi2bsa";*/

        String orderInfo = "Payment";
        String returnUrl = applicationUrl(request) + "/users/momo-payment-result";
        String notifyUrl = "https://d796-101-99-32-135.ngrok-free.app/home";
        String amount = send_amount;
        String orderId = String.valueOf(System.currentTimeMillis());
        String requestId = String.valueOf(System.currentTimeMillis());
        String extraData = bookingId;

        // Before sign HMAC SHA256 signature
        String rawHash = "partnerCode=" +
                partnerCode + "&accessKey=" +
                accessKey + "&requestId=" +
                requestId + "&amount=" +
                amount + "&orderId=" +
                orderId + "&orderInfo=" +
                orderInfo + "&returnUrl=" +
                returnUrl + "&notifyUrl=" +
                notifyUrl + "&extraData=" +
                extraData;

        MoMoSecurity crypto = new MoMoSecurity();
        // Sign signature SHA256
        String signature = crypto.signSHA256(rawHash, serectkey);

        // Build body JSON request
        JSONObject message = new JSONObject();
        message.put("partnerCode", partnerCode);
        message.put("accessKey", accessKey);
        message.put("requestId", requestId);
        message.put("amount", amount);
        message.put("orderId", orderId);
        message.put("orderInfo", orderInfo);
        message.put("returnUrl", returnUrl);
        message.put("notifyUrl", notifyUrl);
        message.put("extraData", extraData);
        message.put("requestType", "captureMoMoWallet");
        message.put("signature", signature);

        System.out.println("Calling MomoWallet Api... ");
        String responseFromMomo = PaymentRequest.sendPaymentRequest(endpoint, message.toString());
        JSONObject jmessage = new JSONObject(responseFromMomo);
        String payUrl = jmessage.getString("payUrl");
        return payUrl;
    }

    @GetMapping("/momo-payment-result")
    public String momoPaymentResult(@RequestParam("amount") String amount,
                                    @RequestParam("errorCode") String errorCode,
                                    @RequestParam("extraData") String bookingId, Model model, final HttpServletRequest request,
                                    @RequestParam(value = "sentEmail", required = false) String sentEmail, Principal p) {
        if (errorCode.equals("0")) {
            String[] parts = bookingId.split("_");
            String part1 = parts[0];
            String part2 = parts.length > 1 ? parts[1] : "";
            if (!part2.isBlank()) {
                Booking booking = bookingService.get(Integer.parseInt(part2));
                booking.setIsPaid(true);
                Booking myBooking = bookingService.save(booking);
                List<Seat> reservedSeat = seatReservationService.getReservedSeat(myBooking);
                model.addAttribute("totalPrice2", amount);
                model.addAttribute("myBooking2", myBooking);
                model.addAttribute("seatsReserved2", reservedSeat);
                String paymentMethod = "Thanh toán Momo ID: " + part2;
                model.addAttribute("paymentMethod2", paymentMethod);
                model.addAttribute("hasRoundTrip", true);
            }
            Booking booking2 = bookingService.get(Integer.parseInt(part1));
            booking2.setIsPaid(true);
            Booking myBooking2 = bookingService.save(booking2);
            List<Seat> reservedSeat2 = seatReservationService.getReservedSeat(myBooking2);
            if (sentEmail == null) {
                sendEmail(part2, request, amount, myBooking2, reservedSeat2);
                System.out.println("Sending email...");
                //Gửi sms
                UserEntity checkUser = userService.findbyEmail(p.getName()).get();
                String destinyPhone = checkUser.getAddress();
                if (destinyPhone != null) {
                    if (validatePhoneNumber(destinyPhone)) {
                        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
                        System.out.println("Sending sms...");
                        String message = "Cám ơn bạn đã đặt vé tại Travelista, phương thức thanh toán MOMO. Tuyến: " + myBooking2.getTrip().getRoute().getName() + ", lúc: " + myBooking2.getTrip().getStartTime() + ", mã vé: " + myBooking2.getBookingDetails().get(0).getId().getTicketCode() + ". Vui lòng mang theo thông tin email hoặc tin nhắn này để soát vé trước khi lên xe, hãy kiểm tra email của bạn để xem chi tiết vé";
                        // Sử dụng thư viện Twilio để gửi tin nhắn SMS
                        Message.creator(
                                new PhoneNumber(destinyPhone), // Số điện thoại người nhận (+84 + số điện thoại)
                                new PhoneNumber(TWILIO_PHONE_NUMBER), // Số điện thoại nguồn (Twilio phone number)
                                message// Nội dung tin nhắn
                        ).create();
                    }
                }

                model.addAttribute("bookingTransit", true);
            } else model.addAttribute("bookingTransit", false);
            model.addAttribute("totalPrice", amount);
            model.addAttribute("myBooking", myBooking2);
            model.addAttribute("seatsReserved", reservedSeat2);
            String paymentMethod = "Thanh toán Momo ID: " + part1;
            model.addAttribute("paymentMethod", paymentMethod);
            model.addAttribute("header", "Đặt vé thành công.");
            model.addAttribute("currentPage", "Hóa đơn");
            return "pages/payment_result";
        } else {
            model.addAttribute("message", "Xảy ra lỗi trong quá trình thanh toán");
            model.addAttribute("header", "Xảy ra lỗi");
            model.addAttribute("currentPage", "Lỗi");
            return "error_message";
        }

    }

    private void sendEmail(String roundTripId, HttpServletRequest request, String totalPrice, Booking myBooking, List<Seat> reservedSeat) {
        List<String> ticketCodes = bookingDetailsService.getTicketCodes(myBooking);
        String send_ticketCodes = "";
        for (String tc : ticketCodes) {
            String code = tc;
            send_ticketCodes += code + ", ";
        }
        send_ticketCodes = send_ticketCodes.substring(0, send_ticketCodes.length() - 2);

        String send_numberOfTicket = String.valueOf(reservedSeat.size());
        String send_reservedSeatNames = ""; // Chuỗi để lưu tên các ghế đã đặt
        for (Seat seat : reservedSeat) {
            String seatName = seat.getName();
            send_reservedSeatNames += seatName + ", "; // Thêm tên của ghế vào chuỗi
        }
        send_reservedSeatNames = send_reservedSeatNames.substring(0, send_reservedSeatNames.length() - 2);

        String currentUrl = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (queryString != null) {
            currentUrl += "?" + queryString;
        }
        publisher.publishEvent(new BookingCompleteEvent(myBooking, roundTripId, totalPrice, send_reservedSeatNames, send_numberOfTicket, send_ticketCodes, currentUrl));
    }

    private boolean validatePhoneNumber(String phoneNumber) {
        // Định dạng pattern
        String pattern = "(\\+84)\\d{9,10}";

        // Tạo đối tượng Pattern từ pattern
        Pattern compiledPattern = Pattern.compile(pattern);

        // Tạo đối tượng Matcher để so khớp pattern với số điện thoại
        Matcher matcher = compiledPattern.matcher(phoneNumber);

        // Kiểm tra khớp pattern
        return matcher.matches();
    }

}

