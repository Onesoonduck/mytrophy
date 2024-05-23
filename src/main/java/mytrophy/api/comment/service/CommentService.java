package mytrophy.api.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mytrophy.api.comment.dto.CommentDto;
import mytrophy.api.comment.entity.Comment;
import mytrophy.api.comment.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;

    //댓글 작성
    public CommentDto createComment(CommentDto commentDto) {
        Comment comment = dtoToEntity(commentDto);
        Comment createdComment = commentRepository.save(comment);
        return entityToDto(createdComment);     //dto로 반환
    }

    //댓글 수정
    public CommentDto updateComment(Long commentId, String content){
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글을 찾을 수 없습니다."));

        comment.updateContent(content);
        return entityToDto(commentRepository.save(comment));
    }

    //댓글 삭제
    public void deleteComment(Long commentId) {
        //권한 확인 로직 추가하기
        //내용만 삭제되었다고 변경 ?
        commentRepository.deleteById(commentId);
    }

    //특정 게시글의 댓글 전체조회
    @Transactional(readOnly = true)
    public List<CommentDto> findByArticleId(Long articleId) {
        List<Comment> comments = commentRepository.findByArticleId(articleId);
        return comments.stream()
                .map(this::entityToDto)
                .collect(Collectors.toList());
    }

    //특정 회원의 댓글 전체조회
    @Transactional(readOnly = true)
    public List<CommentDto> findByMemberId(Long memberId) {
        List<Comment> comments = commentRepository.findByMemberId(memberId);
        return comments.stream()
                .map(this::entityToDto)
                .collect(Collectors.toList());
    }

    //게시글 별 댓글 수 조회
    @Transactional(readOnly = true)
    public int countByArticleId(Long articleId){
        return commentRepository.countByArticleId(articleId);
    }

    //댓글 추천

    //엔티티 -> dto
    private CommentDto entityToDto(Comment comment) {
        return new CommentDto(
                comment.getId(),
                comment.getContent(),
                comment.getMemberId(),
                comment.getArticleId(),
                comment.getCntUp()
        );
    }

    //dto -> 엔티티
    private Comment dtoToEntity(CommentDto commentDto) {
        return Comment.builder()
                .content(commentDto.getContent())
                .memberId(commentDto.getMemberId())
                .articleId(commentDto.getArticleId())
                .cntUp(commentDto.getCntUp())
                .build();
    }

}
