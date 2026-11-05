package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.application.ParticipatedPollReader;
import org.kwakmunsu.auddum.poll.application.dto.ParticipatedPoll;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포트 구현(ParticipatedPollReader, 구현 VoteParticipatedPollReader) 통합 테스트(H2). 이슈 #163 sketch '참여 목록 계약'(Q7-B, Q11) 기준이다.
 * findAllPublicByMemberId(memberId, cursorId, size)는 공개 투표만, vote.id 내림차순, cursorId보다 작은 vote.id만(null이면 첫 쪽) 담은 Slice를 돌려주고
 * countPublicByMemberId(memberId)는 같은 조건의 전체 개수를 센다. 비회원 참여와 숨김·삭제 투표는 포함하지 않는다.
 * hasNext는 필터를 통과한 뒤 size개 너머에 한 건이라도 더 있는지로 정해진다. 포트 인터페이스로 주입받아 계약만 본다.
 */
@RequiredArgsConstructor
@Transactional
class VoteParticipatedPollReaderTest extends VoteIntegrationTestSupport {

    final ParticipatedPollReader participatedPollReader;

    // ---- 범위와 정렬 ----

    @Test
    void 회원의_공개_투표_참여를_vote_id_내림차순으로_돌려준다() {
        var memberId = saveMember();
        var pollA = savePoll();
        var pollB = savePoll();
        var pollC = savePoll();
        var voteB = cast(pollB, memberId);
        var voteC = cast(pollC, memberId);
        var voteA = cast(pollA, memberId);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 20);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId()).containsExactly(
            tuple(voteA, pollA.getId()),
            tuple(voteC, pollC.getId()),
            tuple(voteB, pollB.getId()));
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 쌍의_voteId는_참여_기록_id이고_poll은_해당_투표다() {
        var memberId = saveMember();
        var poll = savePoll();
        var voteId = cast(poll, memberId);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 20);

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().getFirst().voteId()).isEqualTo(voteId);
        assertThat(found.getContent().getFirst().poll().getId()).isEqualTo(poll.getId());
        assertThat(found.getContent().getFirst().poll().getPublicId()).isEqualTo(poll.getPublicId());
    }

    @Test
    void 비회원_익명_참여_행은_포함하지_않는다() {
        var memberId = saveMember();
        var memberPoll = savePoll();
        var anonymousPoll = savePoll();
        var memberVoteId = cast(memberPoll, memberId);
        saveVote(anonymousPoll, 1, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 20);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId())
            .containsExactly(tuple(memberVoteId, memberPoll.getId()));
        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(1L);
    }

    @Test
    void 다른_회원의_참여는_포함하지_않는다() {
        var memberId = saveMember();
        var mine = savePoll();
        var others = savePoll();
        var myVoteId = cast(mine, memberId);
        cast(others, saveMember());

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 20);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId())
            .containsExactly(tuple(myVoteId, mine.getId()));
        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(1L);
    }

    @Test
    void 참여한_적_없는_회원은_빈_쪽이고_개수는_0이다() {
        var poll = savePoll();
        cast(poll, saveMember());
        saveVote(poll, 2, Voter.anonymous(new AnonId(UUID.randomUUID())));
        var stranger = saveMember();

        var found = participatedPollReader.findAllPublicByMemberId(stranger, null, 20);

        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
        assertThat(participatedPollReader.countPublicByMemberId(stranger)).isZero();
    }

    @Test
    void 숨김_투표에_한_참여는_포함하지_않는다() {
        var memberId = saveMember();
        var visible = savePoll();
        var hidden = savePoll();
        var visibleVoteId = cast(visible, memberId);
        cast(hidden, memberId);
        hidden.hide();
        pollJpaRepository.saveAndFlush(hidden);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 20);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId())
            .containsExactly(tuple(visibleVoteId, visible.getId()));
        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(1L);
    }

    @Test
    void 삭제된_투표에_한_참여는_포함하지_않는다() {
        var memberId = saveMember();
        var visible = savePoll();
        var deleted = savePoll();
        var visibleVoteId = cast(visible, memberId);
        cast(deleted, memberId);
        deleted.deleteByAdmin();
        pollJpaRepository.saveAndFlush(deleted);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 20);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId())
            .containsExactly(tuple(visibleVoteId, visible.getId()));
        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(1L);
    }

    // ---- 커서 ----

    @Test
    void 커서보다_작은_vote_id만_돌려주고_커서_자신은_제외한다() {
        var memberId = saveMember();
        var voteIds = castOnNewPolls(memberId, 4);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, voteIds.get(2), 20);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId)
            .containsExactly(voteIds.get(1), voteIds.get(0));
    }

    @Test
    void 가장_오래된_vote_id를_커서로_주면_빈_쪽이고_다음_쪽이_없다() {
        var memberId = saveMember();
        var voteIds = castOnNewPolls(memberId, 3);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, voteIds.getFirst(), 20);

        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 쪽을_이어_받으면_빠짐_중복_없이_끝나고_마지막_쪽에_다음_쪽이_없다() {
        var memberId = saveMember();
        var newestFirst = castOnNewPolls(memberId, 5).reversed();

        var first = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);
        var second = participatedPollReader.findAllPublicByMemberId(memberId, first.getContent().getLast().voteId(), 2);
        var third = participatedPollReader.findAllPublicByMemberId(memberId, second.getContent().getLast().voteId(), 2);

        assertThat(first.getContent()).extracting(ParticipatedPoll::voteId).containsExactlyElementsOf(newestFirst.subList(0, 2));
        assertThat(second.getContent()).extracting(ParticipatedPoll::voteId).containsExactlyElementsOf(newestFirst.subList(2, 4));
        assertThat(third.getContent()).extracting(ParticipatedPoll::voteId).containsExactly(newestFirst.get(4));
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isTrue();
        assertThat(third.hasNext()).isFalse();
    }

    // ---- 쪽 크기와 hasNext ----

    @Test
    void 남은_개수가_size와_같으면_다음_쪽이_없다() {
        var memberId = saveMember();
        castOnNewPolls(memberId, 2);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);

        assertThat(found.getContent()).hasSize(2);
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 남은_개수가_size보다_하나_많으면_size개만_돌려주고_다음_쪽이_있다() {
        var memberId = saveMember();
        castOnNewPolls(memberId, 3);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);

        assertThat(found.getContent()).hasSize(2);
        assertThat(found.hasNext()).isTrue();
    }

    @Test
    void size가_1이면_한_개와_다음_쪽을_돌려준다() {
        var memberId = saveMember();
        castOnNewPolls(memberId, 2);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 1);

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.hasNext()).isTrue();
    }

    @Test
    void size가_50이고_51건이면_50개와_다음_쪽을_돌려준다() {
        var memberId = saveMember();
        castOnNewPolls(memberId, 51);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 50);

        assertThat(found.getContent()).hasSize(50);
        assertThat(found.hasNext()).isTrue();
    }

    // ---- 필터와 쪽 경계 (Q11) ----

    @Test
    void 숨김_삭제가_참여_사이에_섞여도_공개만_size만큼_채우고_다음_쪽이_있다() {
        var memberId = saveMember();
        var oldest = savePoll();
        var hiddenMid = savePoll();
        var middle = savePoll();
        var deletedMid = savePoll();
        var recent = savePoll();
        cast(oldest, memberId);
        cast(hiddenMid, memberId);
        var middleVoteId = cast(middle, memberId);
        cast(deletedMid, memberId);
        var recentVoteId = cast(recent, memberId);
        hiddenMid.hide();
        deletedMid.deleteByAdmin();
        pollJpaRepository.saveAndFlush(hiddenMid);
        pollJpaRepository.saveAndFlush(deletedMid);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId()).containsExactly(
            tuple(recentVoteId, recent.getId()),
            tuple(middleVoteId, middle.getId()));
        assertThat(found.hasNext()).isTrue();
    }

    @Test
    void 쪽_경계에_걸린_숨김_삭제를_건너뛰어_빠짐_중복_없이_이어진다() {
        var memberId = saveMember();
        var first = savePoll();
        var hiddenBetween = savePoll();
        var second = savePoll();
        var deletedBetween = savePoll();
        var third = savePoll();
        var firstVoteId = cast(first, memberId);
        cast(hiddenBetween, memberId);
        var secondVoteId = cast(second, memberId);
        cast(deletedBetween, memberId);
        var thirdVoteId = cast(third, memberId);
        hiddenBetween.hide();
        deletedBetween.deleteByAdmin();
        pollJpaRepository.saveAndFlush(hiddenBetween);
        pollJpaRepository.saveAndFlush(deletedBetween);

        var page1 = participatedPollReader.findAllPublicByMemberId(memberId, null, 1);
        var page2 = participatedPollReader.findAllPublicByMemberId(memberId, page1.getContent().getLast().voteId(), 1);
        var page3 = participatedPollReader.findAllPublicByMemberId(memberId, page2.getContent().getLast().voteId(), 1);

        assertThat(page1.getContent()).extracting(ParticipatedPoll::voteId).containsExactly(thirdVoteId);
        assertThat(page2.getContent()).extracting(ParticipatedPoll::voteId).containsExactly(secondVoteId);
        assertThat(page3.getContent()).extracting(ParticipatedPoll::voteId).containsExactly(firstVoteId);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page2.hasNext()).isTrue();
        assertThat(page3.hasNext()).isFalse();
    }

    @Test
    void 필터_뒤_남은_개수가_정확히_size이면_더_오래된_숨김이_있어도_다음_쪽이_없다() {
        var memberId = saveMember();
        var hiddenOld = savePoll();
        var one = savePoll();
        var two = savePoll();
        cast(hiddenOld, memberId);
        var oneVoteId = cast(one, memberId);
        var twoVoteId = cast(two, memberId);
        hiddenOld.hide();
        pollJpaRepository.saveAndFlush(hiddenOld);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId).containsExactly(twoVoteId, oneVoteId);
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 필터_뒤_size보다_하나_많으면_사이에_숨김이_있어도_다음_쪽이_있다() {
        var memberId = saveMember();
        var oldest = savePoll();
        var hiddenMid = savePoll();
        var one = savePoll();
        var two = savePoll();
        cast(oldest, memberId);
        cast(hiddenMid, memberId);
        cast(one, memberId);
        cast(two, memberId);
        hiddenMid.hide();
        pollJpaRepository.saveAndFlush(hiddenMid);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);

        assertThat(found.getContent()).hasSize(2);
        assertThat(found.hasNext()).isTrue();
    }

    @Test
    void 가장_최근_참여_여러_건이_숨김_삭제여도_공개_투표를_건너뛰지_않고_size만큼_채운다() {
        var memberId = saveMember();
        var oldest = savePoll();
        var middle = savePoll();
        var recent = savePoll();
        var hiddenA = savePoll();
        var hiddenB = savePoll();
        cast(oldest, memberId);
        var middleVoteId = cast(middle, memberId);
        var recentVoteId = cast(recent, memberId);
        cast(hiddenA, memberId);
        cast(hiddenB, memberId);
        hiddenA.hide();
        hiddenB.deleteByAdmin();
        pollJpaRepository.saveAndFlush(hiddenA);
        pollJpaRepository.saveAndFlush(hiddenB);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, null, 2);

        assertThat(found.getContent()).extracting(ParticipatedPoll::voteId, p -> p.poll().getId()).containsExactly(
            tuple(recentVoteId, recent.getId()),
            tuple(middleVoteId, middle.getId()));
        assertThat(found.hasNext()).isTrue();
        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(3L);
    }

    @Test
    void 커서_뒤에_공개_투표가_하나도_없으면_숨김만_남아도_빈_쪽이고_다음_쪽이_없다() {
        var memberId = saveMember();
        var hiddenOld = savePoll();
        var visible = savePoll();
        cast(hiddenOld, memberId);
        var visibleVoteId = cast(visible, memberId);
        hiddenOld.hide();
        pollJpaRepository.saveAndFlush(hiddenOld);

        var found = participatedPollReader.findAllPublicByMemberId(memberId, visibleVoteId, 20);

        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    // ---- 개수 ----

    @Test
    void 개수는_공개_투표만_세고_숨김_삭제_타인_익명_참여는_세지_않는다() {
        var memberId = saveMember();
        var publicPoll = savePoll();
        var hidden = savePoll();
        var deleted = savePoll();
        var othersPoll = savePoll();
        var anonymousPoll = savePoll();
        cast(publicPoll, memberId);
        cast(hidden, memberId);
        cast(deleted, memberId);
        cast(othersPoll, saveMember());
        saveVote(anonymousPoll, 1, Voter.anonymous(new AnonId(UUID.randomUUID())));
        hidden.hide();
        deleted.deleteByAdmin();
        pollJpaRepository.saveAndFlush(hidden);
        pollJpaRepository.saveAndFlush(deleted);

        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(1L);
    }

    @Test
    void 개수는_끝까지_넘긴_목록_길이와_같다() {
        var memberId = saveMember();
        var one = savePoll();
        var hidden = savePoll();
        var two = savePoll();
        var three = savePoll();
        cast(one, memberId);
        cast(hidden, memberId);
        cast(two, memberId);
        cast(three, memberId);
        hidden.hide();
        pollJpaRepository.saveAndFlush(hidden);

        var all = new ArrayList<ParticipatedPoll>();
        Long cursor = null;
        boolean hasNext = true;
        while (hasNext) {
            var slice = participatedPollReader.findAllPublicByMemberId(memberId, cursor, 2);
            all.addAll(slice.getContent());
            hasNext = slice.hasNext();
            cursor = slice.getContent().isEmpty() ? null : slice.getContent().getLast().voteId();
        }

        assertThat(all).hasSize(3);
        assertThat(participatedPollReader.countPublicByMemberId(memberId)).isEqualTo(all.size());
    }

    // ---- 보조 ----

    private Poll savePoll() {
        return savePoll(VoteFixture.openPoll(saveMember()));
    }

}
