package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm
					.setTrainingStartHour(attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm
					.setTrainingStartMinute(attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm
					.setTrainingEndHour(attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm
					.setTrainingEndMinute(attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		Date date = new Date();
		// 入力された情報を更新用のエンティティに移し替え
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			if (dailyAttendanceForm.getTrainingDate() != null && !dailyAttendanceForm.getTrainingDate().isEmpty()) {
				tStudentAttendance.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			}
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}

			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());

			//出勤時刻整形
			Integer startHour = dailyAttendanceForm.getTrainingStartHour();
			Integer startMinute = dailyAttendanceForm.getTrainingStartMinute();
			TrainingTime trainingStartTime = null;

			if (startHour != null && startMinute != null) {
				String startTime = String.format("%02d:%02d", startHour, startMinute);
				trainingStartTime = new TrainingTime(startTime);
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			} else {
				tStudentAttendance.setTrainingStartTime("");
			}
			// 退勤時刻整形
			Integer endHour = dailyAttendanceForm.getTrainingEndHour();
			Integer endMinute = dailyAttendanceForm.getTrainingEndMinute();
			TrainingTime trainingEndTime = null;

			if (endHour != null && endMinute != null) {
				String endTime = String.format("%02d:%02d", endHour, endMinute);
				trainingEndTime = new TrainingTime(endTime);
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			} else {
				tStudentAttendance.setTrainingEndTime("");
			}

			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());

			// 遅刻早退ステータス
			if (trainingStartTime != null && trainingEndTime != null
					&& !"欠席".equals(dailyAttendanceForm.getStatusDispName())) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
						trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			} else if (!"欠席".equals(dailyAttendanceForm.getStatusDispName())) {
				// 時間が未入力なら「NONE（空）」に戻す
				tStudentAttendance.setStatus(AttendanceStatusEnum.NONE.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠管理過去日未入力チェック
	 * 
	 * @author 村瀬菜水香 - Task25
	 * @return 過去日の未入力チェック判定結果
	 */
	public boolean hasUnenteredPastAttendance() {

		Integer lmsUserId = loginUserDto.getLmsUserId();

		Date trainingDate = null;
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
			String today = format.format(new Date());
			trainingDate = format.parse(today);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		Integer notInputCount = tStudentAttendanceMapper.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE, trainingDate);

		return notInputCount != null && notInputCount > 0;
	}

	/**
	 * Task27 更新ボタン押下時入力チェック
	 * 
	 * @author 村瀬
	 * @param attendanceForm
	 * @return エラーメッセージ
	 */
	public List<String> validationAttendanceUpdate(AttendanceForm attendanceForm) {

		
		// 出勤・退勤時間を形成
		for (DailyAttendanceForm dailyForm : attendanceForm.getAttendanceList()) {
			Integer startHour = dailyForm.getTrainingStartHour();
			Integer startMinute = dailyForm.getTrainingStartMinute();
			if (startHour != null && startMinute != null) {
				dailyForm.setTrainingStartTime(String.format("%02d:%02d", startHour, startMinute));
			}

			Integer endHour = dailyForm.getTrainingEndHour();
			Integer endMinute = dailyForm.getTrainingEndMinute();
			if (endHour != null && endMinute != null) {
				dailyForm.setTrainingEndTime(String.format("%02d:%02d", endHour, endMinute));
			}
		}

		int listCount = 0;

		// 日付フォーマット
		SimpleDateFormat[] dateFormats = {
				new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH),
				new SimpleDateFormat("yyyy/M/d")
		};
		SimpleDateFormat dateOnly = new SimpleDateFormat("yyyy/MM/dd");

		List<String> errors = new ArrayList<String>();

		try {
			Date today = dateOnly.parse(dateOnly.format(new Date()));

			for (DailyAttendanceForm dailyForm : attendanceForm.getAttendanceList()) {

				listCount++;

				String dateStr = dailyForm.getTrainingDate();
				if (dateStr == null || dateStr.isEmpty())
					continue;

				// 日付文字列が null/空ならスキップ
				if (dateStr == null || dateStr.trim().isEmpty()) {
					continue;
				}

				// パース処理：複数フォーマットを順に試す
				Date trainingDate = null;
				for (SimpleDateFormat fmt : dateFormats) {
					try {
						trainingDate = fmt.parse(dateStr);
						break;
					} catch (ParseException ignored) {
					}
				}

				// パース失敗（null）の場合はスキップ
				if (trainingDate == null) {
					continue;
				}

				// 今日・未来はスキップ
				Date trainingDateOnly = dateOnly.parse(dateOnly.format(trainingDate));
				if (!trainingDateOnly.before(today))
					continue;

				// 欠席スキップ
				String status = dailyForm.getStatus();
				if (status != null && status.equals(1))
					continue;

				String hasStartTime = dailyForm.getTrainingStartTime();
				String hasEndTime = dailyForm.getTrainingEndTime();
				String hasNote = dailyForm.getNote();
				boolean hasStartHour = dailyForm.getTrainingStartHour() != null;
				boolean hasStartMinute = dailyForm.getTrainingStartMinute() != null;
				boolean hasEndHour = dailyForm.getTrainingEndHour() != null;
				boolean hasEndMinute = dailyForm.getTrainingEndMinute() != null;

				// 備考が100文字を超えていないかチェック
				if (hasNote != null && hasNote.length() > 100) {
					errors.add(messageUtil.getMessage("maxlength", new String[] { "備考", "100" }));
				}

				// 出勤時間の時か分どちらか未入力でないかチェック
				if (hasStartHour ^ hasStartMinute) {
					errors.add(messageUtil.getMessage("input.invalid", new String[] { "出勤時間" }));
				}

				// 退勤時間の時か分どちらかが未入力でないかチェック
				if (hasEndHour ^ hasEndMinute) {
					errors.add(messageUtil.getMessage("input.invalid", new String[] { "退勤時間" }));
				}

				// 出勤時間が未入力の場合で退勤時間に入力がないかチェック
				if (!hasStartHour && !hasStartMinute && (hasEndHour || hasEndMinute)) {
					errors.add(messageUtil.getMessage("attendance.punchInEmpty"));
				}

				//出勤・退勤時間が未入力でない場合にチェックをする
				if (hasStartHour && hasStartMinute && hasEndHour && hasEndMinute) {

					// 退勤時間が出勤時間より前でないかチェック
					SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
					Date start = timeFmt.parse(hasStartTime);
					Date end = timeFmt.parse(hasEndTime);
					if (end.before(start)) {
						errors.add(messageUtil.getMessage("attendance.trainingTimeRange",
								new String[] { String.valueOf(listCount) }));
					}

					// 中抜け時間が受講時間を超えていないかチェック
					TrainingTime juko = attendanceUtil.calcJukoTime(
							new TrainingTime(hasStartTime), new TrainingTime(hasEndTime));
					int jukoMinutes = juko.getHour() * 60 + juko.getMinute();
					Integer blank = dailyForm.getBlankTime();
					if (blank != null && blank > jukoMinutes) {
						errors.add(messageUtil.getMessage("attendance.blankTimeError"));
					}
				}
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return errors;
	}

}
