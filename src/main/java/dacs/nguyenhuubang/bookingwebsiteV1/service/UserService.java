package dacs.nguyenhuubang.bookingwebsiteV1.service;

import dacs.nguyenhuubang.bookingwebsiteV1.dto.RegistrationRequest;
import dacs.nguyenhuubang.bookingwebsiteV1.entity.Provider;
import dacs.nguyenhuubang.bookingwebsiteV1.entity.UserEntity;
import dacs.nguyenhuubang.bookingwebsiteV1.entity.VerificationToken;
import dacs.nguyenhuubang.bookingwebsiteV1.exception.ResourceNotFoundException;
import dacs.nguyenhuubang.bookingwebsiteV1.exception.UserAlreadyExistsException;
import dacs.nguyenhuubang.bookingwebsiteV1.exception.UserNotFoundException;
import dacs.nguyenhuubang.bookingwebsiteV1.repository.UserRepository;
import dacs.nguyenhuubang.bookingwebsiteV1.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository;

    public void createNewUserAfterOauthLoginSuccess(String email, String name, Provider provider){
        UserEntity newUser = new UserEntity();
        newUser.setFullname(name);
        newUser.setEmail(email);
        newUser.setEnabled(true);
        newUser.setProvider(provider);
        newUser.setRole("USER");
         userRepository.save(newUser);
    }

    @Override
    public List<UserEntity> getUsers() {
        return (List<UserEntity>)userRepository.findAll();
    }

    public List<UserEntity> search(String keyword) {
        if (keyword != null) {
            return userRepository.search(keyword);
        }
        return userRepository.findAll();
    }

    @Override
    public UserEntity registerUser(RegistrationRequest request) {
        Optional<UserEntity> user = this.findbyEmail(request.email());
        if (user != null) {
            throw new UserAlreadyExistsException("Người dùng với email " + request.email() + " đã tồn tại!");
        }
        var newUser = new UserEntity();
        newUser.setFullname(request.fullname());
        // Xóa bỏ các ký tự không phải số
        String cleanedNumber = request.address().replaceAll("[^\\d]", "");
        // Kiểm tra nếu số điện thoại bắt đầu bằng "0"
        if (cleanedNumber.startsWith("0")) {
            // Thay thế "0" đầu tiên bằng "+84"
            cleanedNumber = "+84" + cleanedNumber.substring(1);
        }
        newUser.setAddress(cleanedNumber);
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole(request.role());
        return userRepository.save(newUser);
    }

    @Override
    public Optional<UserEntity> findbyEmail(String email) {
        Optional<UserEntity> result = userRepository.findByEmail(email);
        if (result.isPresent()){
            return result;
        }
        else return null;

    }

    @Override
    public void saveUserVerificationToken(UserEntity user, String token) {
        var verificationToken = new VerificationToken(token, user);
        tokenRepository.save(verificationToken);
    }

    @Override
    public String validateToken(String theToken) {
        VerificationToken token = tokenRepository.findByToken(theToken);
        if (token==null){
            return "Invalid verification token";
        }
        UserEntity user = token.getUser();
        Calendar calendar = Calendar.getInstance();
        if ((token.getExpirationTime().getTime() - calendar.getTime().getTime()) <= 0){
            //tokenRepository.delete(token);
            return "Token already expired!";
        }
        user.setEnabled(true);
        userRepository.save(user);
        return "Valid";
    }

    @Override
    public VerificationToken generateNewVerificationToken(String oldToken) {
        VerificationToken verificationToken = tokenRepository.findByToken(oldToken);
        if (verificationToken == null) {
            throw new ResourceNotFoundException("Token " + oldToken + " is not valid");
        }
        var verificationTokenTime = new VerificationToken();
        verificationToken.setToken(UUID.randomUUID().toString());
        verificationToken.setExpirationTime(verificationTokenTime.getTokenExpirationTime());
        return tokenRepository.save(verificationToken);
    }

    @Override
    public void save(UserEntity user) {
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof SQLIntegrityConstraintViolationException) {
                throw new UserAlreadyExistsException("User with email " + user.getEmail() + " already exists");
            } else {
                throw e;
            }
        }
    }


    public UserEntity get(Integer id){
        Optional<UserEntity> result = userRepository.findById(id);
        if (result.isPresent()){
            return result.get();
        }
        else
            throw new UserNotFoundException("Không tìm thấy người dùng với ID: " + id + "!");
    }

    public void delete(Integer id) {
        Long count = userRepository.countById(id);
        if (count == null || count == 0) {
            throw new UserNotFoundException("Không tìm thấy người dùng với ID " + id);
        }
        userRepository.deleteTokenByUserId(id);
        userRepository.deleteById(id);
    }

    public Page<UserEntity> findPaginated(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return this.userRepository.findAll(pageable);
    }

    public Page<UserEntity> findPaginated(int pageNo, int pageSize, String sortField, String sortDirection) {
        Sort sort = sortDirection.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();

        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);
        return this.userRepository.findAll(pageable);
    }

    public void updateCustomerAfterOauthLoginSuccess(UserEntity userEntity, String fullName, Provider provider) {
        userEntity.setProvider(provider);
        userEntity.setFullname(fullName);
        userRepository.save(userEntity);
    }

    public Optional<UserEntity> findByGithubUserName(String loginName) {
        Optional<UserEntity> result = userRepository.findByGithubUserName(loginName);
        if (result.isPresent()) {
            return result;
        } else return null;
    }

    public void updateResetPassword(String token, UserEntity user) throws UserNotFoundException {
        user.setResetPasswordToken(token);
        userRepository.save(user);
    }

    public UserEntity getResetPasswordToken(String resetPasswordToken) {
        return userRepository.findByResetPasswordToken(resetPasswordToken);
    }

    public void updatePassword(UserEntity user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        userRepository.save(user);
    }
}

