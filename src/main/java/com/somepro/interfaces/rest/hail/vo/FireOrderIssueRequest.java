package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 下达作业指令请求体（用户接口层，不可变 record）。
 *
 * 记上：哪条已批空域、用哪台装备、打什么弹型、从哪个批次出、计划打几发、哪段时辰打。
 * 作业时段必须完整落在空域批复的时段里头，起早了、拖晚了都开不出去。
 * 指令编号由后端按年分配；开单即从结存划走计划发数并记 OUT 领用流水。
 */
public record FireOrderIssueRequest(
        @NotNull(message = "空域申请 applyId 不能为空")
        Long applyId,

        @NotNull(message = "执行装备 launcherId 不能为空")
        Long launcherId,

        @NotBlank(message = "弹型不能为空，如 BL-1A")
        @Size(max = 32, message = "弹型最长 32 位")
        String ammoType,

        @NotBlank(message = "出弹批次号不能为空")
        @Size(max = 32, message = "批次号最长 32 位")
        String batchNo,

        @NotNull(message = "计划用弹发数不能为空")
        @Positive(message = "计划用弹发数必须为正整数")
        Integer planRounds,

        @NotNull(message = "作业开始时刻不能为空")
        LocalDateTime planStart,

        @NotNull(message = "作业结束时刻不能为空")
        LocalDateTime planEnd) implements Serializable {
}
