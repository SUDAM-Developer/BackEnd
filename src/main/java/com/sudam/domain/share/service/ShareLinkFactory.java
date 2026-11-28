package com.sudam.domain.share.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 받는 사람이 카카오톡 메시지에서 여는 링크 화면 URL을 만든다.
 * 링크는 참여자마다 하나(/l/{token})이고 화면별 경로를 뒤에 붙인다. 프론트 주소는 프로파일별 app.link.base-url이다.
 */
@Component
public class ShareLinkFactory {

    private final String baseUrl;

    public ShareLinkFactory(@Value("${app.link.base-url}") String baseUrl) {
        // 끝의 '/'를 떼어 두어 경로를 붙일 때 '//'가 생기지 않게 한다.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /***
     * 함수 기능: 학부모 정산 리포트 화면 URL을 만든다.
     * @param linkToken 참여자 링크 토큰 (URL 안전 문자열이라 인코딩하지 않는다)
     * @param reportId  정산 리포트 ID
     * @return {base}/l/{token}/reports/{reportId}
     */
    public String reportUrl(String linkToken, Long reportId) {
        return linkRoot(linkToken) + "/reports/" + reportId;
    }

    /***
     * 함수 기능: 학부모 교육비 안내 화면 URL을 만든다.
     * @param linkToken 참여자 링크 토큰
     * @param invoiceId 청구 ID
     * @return {base}/l/{token}/invoices/{invoiceId}
     */
    public String invoiceUrl(String linkToken, Long invoiceId) {
        return linkRoot(linkToken) + "/invoices/" + invoiceId;
    }

    /***
     * 함수 기능: 과외 초대 링크(참여자 링크 루트) URL을 만든다.
     * @param linkToken 참여자 링크 토큰
     * @return {base}/l/{token}
     */
    public String inviteUrl(String linkToken) {
        return linkRoot(linkToken);
    }

    private String linkRoot(String linkToken) {
        return baseUrl + "/l/" + linkToken;
    }
}
