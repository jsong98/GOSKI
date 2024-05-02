package com.go.ski.payment.core.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.go.ski.payment.core.model.Lesson;
import com.go.ski.payment.core.model.LessonInfo;
import com.go.ski.payment.core.model.LessonPaymentInfo;
import com.go.ski.payment.core.model.Payment;
import com.go.ski.payment.core.model.StudentInfo;
import com.go.ski.payment.core.repository.ChargeRepository;
import com.go.ski.payment.core.repository.LessonInfoRepository;
import com.go.ski.payment.core.repository.LessonPaymentInfoRepository;
import com.go.ski.payment.core.repository.LessonRepository;
import com.go.ski.payment.core.repository.PaymentRepository;
import com.go.ski.payment.core.repository.SettlementRepository;
import com.go.ski.payment.core.repository.StudentInfoRepository;
import com.go.ski.payment.support.dto.request.ApprovePaymentRequestDTO;
import com.go.ski.payment.support.dto.request.CancelPaymentRequestDTO;
import com.go.ski.payment.support.dto.request.KakaopayApproveRequestDTO;
import com.go.ski.payment.support.dto.request.KakaopayCancelRequestDTO;
import com.go.ski.payment.support.dto.request.KakaopayPrepareRequestDTO;
import com.go.ski.payment.support.dto.request.ReserveLessonPaymentRequestDTO;
import com.go.ski.payment.support.dto.response.KakaopayApproveResponseDTO;
import com.go.ski.payment.support.dto.response.KakaopayCancelResponseDTO;
import com.go.ski.payment.support.dto.response.KakaopayPrepareResponseDTO;
import com.go.ski.payment.support.dto.response.OwnerPaymentHistoryResponseDTO;
import com.go.ski.payment.support.dto.response.UserPaymentHistoryResponseDTO;
import com.go.ski.payment.support.dto.response.WithdrawalResponseDTO;
import com.go.ski.payment.support.dto.util.StudentInfoDTO;
import com.go.ski.payment.support.dto.util.TotalPaymentDTO;
import com.go.ski.payment.support.dto.util.TotalSettlementDTO;
import com.go.ski.redis.dto.PaymentCacheDto;
import com.go.ski.redis.repository.PaymentCacheRepository;
import com.go.ski.team.core.model.Team;
import com.go.ski.team.core.repository.TeamRepository;
import com.go.ski.team.support.dto.TeamResponseDTO;
import com.go.ski.user.core.model.Instructor;
import com.go.ski.user.core.model.User;
import com.go.ski.user.core.repository.InstructorRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {
	@Value("${pay.test_id}")
	public String testId;
	@Value("${pay.client_id}")
	public String clientId;
	@Value("${pay.client_secret}")
	public String clientSecret;
	@Value("${pay.secret_key}")
	public String secretKey;
	@Value("${pay.secret_key_dev}")
	public String secretKeyDev;
	@Value("${pay.approval_url}")
	public String approvalUrl;
	@Value("${pay.cancel_url}")
	public String cancelUrl;
	@Value("${pay.fail_url}")
	public String failUrl;
	private String HOST = "https://open-api.kakaopay.com/online/v1/payment";
	private final RestTemplate restTemplate = new RestTemplate();

	private final TeamRepository teamRepository;
	private final InstructorRepository instructorRepository;
	private final LessonRepository lessonRepository;
	private final LessonInfoRepository lessonInfoRepository;
	private final LessonPaymentInfoRepository lessonPaymentInfoRepository;
	private final StudentInfoRepository studentInfoRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCacheRepository paymentCacheRepository;
	private final SettlementRepository settlementRepository;
	private final ChargeRepository chargeRepository;

	//카카오 페이에 보낼 요청의 헤더 값을 넣어주는 메소드
	public HttpHeaders getHeader(String mode) {
		HttpHeaders headers = new HttpHeaders();

		if(mode.equals("test")) headers.add("Authorization", "SECRET_KEY " + secretKeyDev);
		else headers.add("Authorization", "SECRET_KEY " + secretKey);

		headers.add("Content-type", "application/json");

		return headers;
	}
	// 사용자의 요청으로 생성되기 때문에 isOwn이 그냥 자체 서비스임 HttpServletRequest
	@Transactional
	public KakaopayPrepareResponseDTO getPrepareResponse(
		HttpServletRequest httpServletRequest,
		ReserveLessonPaymentRequestDTO request) {
		//예약자
		User user = (User)httpServletRequest.getAttribute("user");
		Team team = teamRepository.findById(request.getTeamId()).orElseThrow();
		Instructor instructor;
		if(request.getInstId()!=null) instructor = instructorRepository.findById(request.getInstId()).get();
		else instructor = null;

		int size = request.getStudentInfo().size();
		// 레슨 생성
		Lesson lesson = Lesson.toLessonForPayment(user, team, instructor, 0);
		LessonInfo lessonInfo = LessonInfo.toLessonInfoForPayment(request);

		// 2번 생성된 예약정보를 바탕으로 kakaoPayService에 필요한 변수를 넣기
		Integer basicFee = request.getBasicFee();
		Integer designatedFee = request.getDesignatedFee();
		Integer peopleOptionFee = request.getPeopleOptionFee();
		Integer levelOptionFee = request.getLevelOptionFee();

		LessonPaymentInfo lessonPaymentInfo = LessonPaymentInfo
			.toLessonPaymentInfoForPayment(basicFee, designatedFee, peopleOptionFee, levelOptionFee, request.getDuration());
		Integer totalFee = basicFee + designatedFee + peopleOptionFee + levelOptionFee;
		String itemName = team.getTeamName() + " 팀, 예약자 : " + user.getUserName() + " 외 " + size + "명";

		//만들어서 KAKAO랑 소통하기
		KakaopayPrepareRequestDTO kakaopayPrepareRequestDTO = KakaopayPrepareRequestDTO.toKakaopayPrepareRequestDTO(lesson, itemName, totalFee);
		KakaopayPrepareResponseDTO kakaopayPrepareResponseDTO = requestPrepareToKakao(kakaopayPrepareRequestDTO);

		String tid = kakaopayPrepareResponseDTO.getTid();
		PaymentCacheDto paymentCache = PaymentCacheDto.builder()
			.tid(tid)
			.lesson(lesson)
			.lessonInfo(lessonInfo)
			.lessonPaymentInfo(lessonPaymentInfo)
			.studentInfos(request.getStudentInfo())
			.build();

		paymentCacheRepository.save(paymentCache);
		return kakaopayPrepareResponseDTO;
	}

	// 결제 승인
	// 결제 테이블 생성해야함
	@Transactional
	public KakaopayApproveResponseDTO getApproveResponse(
		HttpServletRequest httpServletRequest,
		ApprovePaymentRequestDTO request) {

		User user = (User)httpServletRequest.getAttribute("user");

		PaymentCacheDto paymentCache = paymentCacheRepository
			.findById(request.getTid())
			.orElseThrow();

		Lesson tmpLesson = lessonRepository.save(paymentCache.getLesson());
		LessonInfo tmpLessonInfo = LessonInfo.builder()
			.lessonId(tmpLesson.getLessonId())
			.lesson(tmpLesson)
			.lessonDate(paymentCache.getLessonInfo().getLessonDate())
			.startTime(paymentCache.getLessonInfo().getStartTime())
			.duration(paymentCache.getLessonInfo().getDuration())
			.lessonType(paymentCache.getLessonInfo().getLessonType())
			.studentCount(paymentCache.getLessonInfo().getStudentCount())
			.build();

		lessonInfoRepository.save(tmpLessonInfo);
		for (StudentInfoDTO studentInfoDTO : paymentCache.getStudentInfos()) {
			StudentInfo tmpStudentInfo = StudentInfo.toStudentInfoForPayment(tmpLessonInfo, studentInfoDTO);
			studentInfoRepository.save(tmpStudentInfo);
		}

		LessonPaymentInfo tmpLessonPaymentInfo = LessonPaymentInfo.builder()
			.lessonId(tmpLesson.getLessonId())
			.lesson(tmpLesson)
			.basicFee(paymentCache.getLessonPaymentInfo().getBasicFee())
			.designatedFee(paymentCache.getLessonPaymentInfo().getDesignatedFee())
			.levelOptionFee(paymentCache.getLessonPaymentInfo().getLevelOptionFee())
			.peopleOptionFee(paymentCache.getLessonPaymentInfo().getPeopleOptionFee())
			.build();
		lessonPaymentInfoRepository.save(tmpLessonPaymentInfo);

		Payment tmpPayment = Payment.builder()
			.LessonPaymentInfo(tmpLessonPaymentInfo)
			.totalAmount(tmpLessonPaymentInfo.getBasicFee()
				+ tmpLessonPaymentInfo.getDesignatedFee()
				+ tmpLessonPaymentInfo.getLevelOptionFee()
				+ tmpLessonPaymentInfo.getPeopleOptionFee()
			)
			.chargeId(0)// 사용자 0? 100?
			.paymentDate(LocalDate.now()).build();
		paymentRepository.save(tmpPayment);

		// 이 정보는 굳이 내보내야하나?
		KakaopayApproveRequestDTO kakaopayApproveRequestDTO = KakaopayApproveRequestDTO.builder()
			.tid(request.getTid())
			.partnerOrderId("partner_order_id")//레슨 id
			.partnerUserId(String.valueOf(user.getUserId()))//유저 아이디
			.pgToken(request.getPgToken())//pg_token
			.build();

		return requestApproveToKakao(kakaopayApproveRequestDTO);
	}

	@Transactional
	public KakaopayCancelResponseDTO getCancelResponse(
		HttpServletRequest httpServletRequest,
		CancelPaymentRequestDTO request) {
		//예약자
		User user = (User)httpServletRequest.getAttribute("user");
		//lessonId를 받았음
		//lesson이 없으면 에러 반환
		LessonInfo lessonInfo = lessonInfoRepository.findById(request.getLessonId()).orElseThrow();

		Payment payment = paymentRepository.findByLessonPaymentInfoLessonId(request.getLessonId());
		LocalDate reservationDate = lessonInfo.getLessonDate();

		int compareResult = reservationDate.compareTo(LocalDate.now());
		// 예약일이 지금보다 뒤에 있으면 취소 가능
		// 반환 금액과 chargeId 변경해주기
		long dayDiff = ChronoUnit.DAYS.between(reservationDate, LocalDate.now());
		int chargeId;

		if(compareResult > 0 && dayDiff > 2) {
			// 돈을 바로 송금 해야함
			// 날짜  확인
			// 예약 후 취소시 : 전액 환불
			if(dayDiff > 7) chargeId = 1;
			// 이용일 7일 이전 취소 시 : 예약금의 50% 환불
			else  chargeId = 2;
			// 이용일 3일 이전 취소 시 : 예약금의 30% 환불
			payment = Payment.builder()
				.chargeId(chargeId)
				.build();

			//이걸로 결제 취소 시켜줘야함
			int payback = payment.getTotalAmount() * chargeRepository.findById(1).get().getStudentChargeRate();

			KakaopayCancelRequestDTO kakaopayCancelRequestDTO = KakaopayCancelRequestDTO.builder()
				.tid("tid")
				.cancelAmount(payback)
				.cancelTaxFreeAmount(0)
				.build();
			// 여기서는 스케줄 없애기

			// 여기서 카카오 페이 결제 취소 API 보냄
			return requestCancelToKakao(kakaopayCancelRequestDTO);
		}
		else {
			// 이용일 2일 이전 취소 시 : 환불이 불가
			// Exception 보내기
			// 잘못된 변수 or 부적절한 요청
			payment = Payment.builder()
				.build();
			// 알아서 return
			throw new IllegalArgumentException();
		}
	}

	//카카오 페이에 보내는 준비 요청 메소드
	@Transactional
	public KakaopayPrepareResponseDTO requestPrepareToKakao(KakaopayPrepareRequestDTO request) {
		//헤더 설정 추후에 서비스 모드로 변경
		HttpHeaders headers = getHeader("test");

		Map<String, String> params = new HashMap<>();
		params.put("cid", testId);
		params.put("partner_order_id", request.getPartnerOrderId());
		params.put("partner_user_id", request.getPartnerUserId());//실제로는 무슨 값이 들어가야하는지?
		params.put("item_name", request.getItemName());//팀 이름, 레벨, 명 수 이렇게 묶을 생각임
		params.put("quantity", Integer.toString(request.getQuantity()));// 무조건 1
		params.put("total_amount", Integer.toString(request.getTotalAmount()));// 계산한 값 보내줌
		params.put("tax_free_amount", Integer.toString(request.getTaxFreeAmount()));// 부가가치세가 면제
		params.put("approval_url", approvalUrl);//url 보내줌
		params.put("cancel_url", cancelUrl);//url 보내줌
		params.put("fail_url", failUrl);//url 보내줌

		HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(params, headers);
		ResponseEntity<KakaopayPrepareResponseDTO> responseEntity = restTemplate.postForEntity(HOST + "/ready", requestEntity, KakaopayPrepareResponseDTO.class);

		return responseEntity.getBody();
	}

	//카카오 페이에 거래 승인 요청 메소드
	@Transactional
	public KakaopayApproveResponseDTO requestApproveToKakao(KakaopayApproveRequestDTO request) {
		HttpHeaders headers = getHeader("test");

		Map<String, String> params = new HashMap<>();
		params.put("cid", testId);
		params.put("tid", request.getTid());
		params.put("partner_order_id", request.getPartnerOrderId());
		params.put("partner_user_id", request.getPartnerUserId());
		params.put("pg_token", request.getPgToken());

		HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(params, headers);
		ResponseEntity<KakaopayApproveResponseDTO> responseEntity = restTemplate.postForEntity(HOST + "/approve", requestEntity, KakaopayApproveResponseDTO.class);

		// 여기서 결제 정보를 db에 저장
		return responseEntity.getBody();
	}

	@Transactional
	public KakaopayCancelResponseDTO requestCancelToKakao(KakaopayCancelRequestDTO request) {
		//헤더 설정 추후에 서비스 모드로 변경
		HttpHeaders headers = getHeader("test");

		Map<String, String> params = new HashMap<>();
		params.put("cid", testId);
		params.put("tid", request.getTid());
		params.put("cancel_amount", String.valueOf(request.getCancelAmount()));
		params.put("cancel_tax_free_amount", String.valueOf(request.getCancelTaxFreeAmount()));

		HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(params, headers);
		ResponseEntity<KakaopayCancelResponseDTO> responseEntity = restTemplate.postForEntity(HOST + "/cancel", requestEntity, KakaopayCancelResponseDTO.class);

		return responseEntity.getBody();
	}

	@Transactional
	public List<UserPaymentHistoryResponseDTO> getUserPaymentHistories(HttpServletRequest httpServletRequest) {
		User user = (User)httpServletRequest.getAttribute("user");

		return lessonRepository.findStudentPaymentHistories(user.getUserId());
	}

	@Transactional
	public List<OwnerPaymentHistoryResponseDTO> getOwnerPaymentHistories(HttpServletRequest httpServletRequest) {
		User user = (User)httpServletRequest.getAttribute("user");
		//사장인지 확인
		//내 아래로 팀이 있는지 확인
		List<TeamResponseDTO> dummy = teamRepository.findTeamList(user.getUserId());
		//exception 만들기
		if(dummy.isEmpty()) throw new IllegalArgumentException("조회할 수 없습니다.");
		return lessonRepository.findOwnerPaymentHistories(user.getUserId());
	}
	@Transactional
	public List<OwnerPaymentHistoryResponseDTO> getTeamPaymentHistories(HttpServletRequest httpServletRequest, Integer teamId) {
		User user = (User)httpServletRequest.getAttribute("user");
		boolean b = false;
		//사장인지 확인
		//내 아래로 팀이 있는지 확인
		List<TeamResponseDTO> dummy = teamRepository.findTeamList(user.getUserId());
		for(int id = 0; id < dummy.size(); id++) {
			if(Objects.equals(dummy.get(id).getTeamId(), teamId)) b = true;
		}
		//exception 만들기
		if(!b) throw new IllegalArgumentException("조회할 수 없습니다.");

		return lessonRepository.findTeamPaymentHistories(teamId);
	}
	// 그 동안의 출금 내역을 조회
	@Transactional
	public List<WithdrawalResponseDTO> getWithdrawalList(HttpServletRequest httpServletRequest) {
		User user = (User)httpServletRequest.getAttribute("user");
		return settlementRepository.findWithrawalList(user.getUserId());
	}


	// 가능 금액을 조회
	// 내 총 핀매금액 - 총 정산 금액
	// 정산 가능 금액 구했으면 알아서 다시 요청하지마라 프론트
	// 이거는 뭈쓸모가 맞다
	@Transactional
	public Integer getBalance(HttpServletRequest httpServletRequest) {
		User user = (User)httpServletRequest.getAttribute("user");

		int balance = 0;
		List<TotalPaymentDTO> totalPayments = paymentRepository.findTotalPayment(user.getUserId());
		List<TotalSettlementDTO> totalSettlements = settlementRepository.findBySettlements(user.getUserId());

		for(TotalPaymentDTO tmp : totalPayments) {
			balance += tmp.getChargeRate() * tmp.getTotalAmount() / 100;
		}
		for(TotalSettlementDTO tmp : totalSettlements) {
			balance -= tmp.getSettlementAmount();
		}
		// 잔액 total(현재 기준 예약 날짜 지난것들)
		// minus
		// 출금 내역 리스트
		return balance;
	}
}
