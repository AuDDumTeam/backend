package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.member.application.MemberValidator;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;

/**
 * 기존 참여 검증 단위 테스트(목 VoteReader). 기준은 참여 처리 순서와 P-08·P-12:
 * 이미 참여한 기록이 있으면 선택과 관계없이 409 ALREADY_VOTED이고, 기록이 없으면 통과한다.
 */
class VoteValidatorTest {

    private static final Long MEMBER_ID = 7L;

    private final VoteReader voteReader = mock(VoteReader.class);
    private final MemberJpaRepository memberRepository = mock(MemberJpaRepository.class);
    private final MemberReader memberReader = new MemberReader(memberRepository, mock(MemberAuthJpaRepository.class));
    private final VoteValidator voteValidator = new VoteValidator(voteReader, new MemberValidator(memberReader));
    private final Poll poll = VoteFixture.withIds(VoteFixture.openPoll(1L));
    private final Voter voter = Voter.member(MEMBER_ID);

    // ---- validateVoter: 회원 상태 확인 (정지 403, 탈퇴·없음 401, P-05) ----

    @Test
    void 정상_회원_참여자는_통과한다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.createMember()));

        assertThatCode(() -> voteValidator.validateVoter(voter)).doesNotThrowAnyException();
    }

    @Test
    void 정지_회원_참여자는_MEMBER_SUSPENDED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(
                MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> voteValidator.validateVoter(voter))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 탈퇴한_회원_참여자는_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 19, 12, 0));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> voteValidator.validateVoter(voter))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 없는_회원_참여자는_UNAUTHENTICATED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> voteValidator.validateVoter(voter))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 익명_참여자는_회원_조회를_하지_않고_통과한다() {
        assertThatCode(() -> voteValidator.validateVoter(Voter.of(null, UUID.randomUUID()))).doesNotThrowAnyException();

        verifyNoInteractions(memberRepository);
    }

    // ---- validateNotParticipated ----

    @Test
    void 참여한_기록이_없으면_통과한다() {
        given(voteReader.find(poll, voter)).willReturn(Optional.empty());

        assertThatCode(() -> voteValidator.validateNotParticipated(poll, voter)).doesNotThrowAnyException();
    }

    @Test
    void 참여한_기록이_있으면_ALREADY_VOTED다() {
        given(voteReader.find(poll, voter)).willReturn(Optional.of(Vote.cast(poll, 1, voter, NOW)));

        assertThatThrownBy(() -> voteValidator.validateNotParticipated(poll, voter))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

}
