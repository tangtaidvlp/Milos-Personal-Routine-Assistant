package com.tom.payment.routinemanager.service;

import java.util.List;

import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.UserRepository;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DefaultRoutineService routineService;

    public UserService(UserRepository userRepository, @Lazy DefaultRoutineService routineService) {
        this.userRepository = userRepository;
        this.routineService = routineService;
    }

    @Transactional
    public User createUserAndDefaultRoutine(User user) {
        User savedUser = userRepository.save(user);
        
        // One empty default routine per type: weekday (DAILY) and weekend (specs/.done.002-onboarding-instruction).
        DefaultRoutine weekday = new DefaultRoutine();
        weekday.setName(DefaultRoutineService.WEEKDAY_ROUTINE_NAME);
        weekday.setRoutineType(DefaultRoutine.RoutineTypeEnum.DAILY);
        routineService.createDefaultRoutine(savedUser.getId(), weekday);

        DefaultRoutine weekend = new DefaultRoutine();
        weekend.setName(DefaultRoutineService.WEEKEND_ROUTINE_NAME);
        weekend.setRoutineType(DefaultRoutine.RoutineTypeEnum.WEEKEND);
        routineService.createDefaultRoutine(savedUser.getId(), weekend);
        
        return savedUser;
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public User markOnboardingCompleted(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setOnboardCompleted(true);
        return userRepository.save(user);
    }

    // Fetch all users
    public List<User> getAllUsers() {
        return (List<User>) userRepository.findAll();
    }
}
