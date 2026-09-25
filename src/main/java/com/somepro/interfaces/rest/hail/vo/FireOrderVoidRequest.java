package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;

/**
 * 作业指令作废请求体（用户接口层，不可变 record）。
 *
 * 收摊作废：只有一发都没打的在途单子能作废；原因可空。
 * 作废幂等，连点两遍第二遍原样返回、不再退弹。
 */
public record FireOrderVoidRequest(String reason) implements Serializable {
}
