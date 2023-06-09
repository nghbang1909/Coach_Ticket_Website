package dacs.nguyenhuubang.bookingwebsiteV1.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
@Entity
@Getter
@Setter
@Table(name = "booking")
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonProperty("trip")
    @JoinColumn(name = "trip_id", referencedColumnName = "id", nullable = false)
    @NotNull(message = "Trip is required")
    private Trip trip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonProperty("user")
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @NotNull
    @Column(name = "isPaid")
    private Boolean isPaid = false;

    @NotNull
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Size(max = 1000, message = "Nội dung không được vượt quá 1000 ký tự")
    @Column(name = "note", nullable = true)
    private String note = null;

    @Column(name = "user_address", nullable = true)
    private String userAddress = null;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<SeatReservation> seatReservations;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<BookingDetails> bookingDetails;

    @OneToOne(mappedBy = "booking", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JsonIgnore
    private ShuttleBus shuttleBus;


}
