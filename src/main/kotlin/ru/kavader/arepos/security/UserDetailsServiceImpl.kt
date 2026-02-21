package ru.kavader.arepos.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import ru.kavader.arepos.repository.UsersRepository

@Service
class UserDetailsServiceImpl(
    private val usersRepository: UsersRepository
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = usersRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("User not found with email: $email")

        return User.builder()
            .username(user.id.toString())
            .password(user.passwordHash ?: "")
            .authorities(SimpleGrantedAuthority("ROLE_${user.role.name}"))
            .disabled(!user.isActive)
            .build()
    }
}
