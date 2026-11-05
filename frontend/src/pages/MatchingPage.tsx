export function MatchingPage() {
  return (
    <div className="container">
      <div className="card">
        <h1>매칭 진행 상태</h1>
        <div className="grid two-col">
          <div>
            <h3>진행 단계</h3>
            <ul>
              <li>요청 구조화 완료</li>
              <li>안전 검토 완료</li>
              <li>후보 찾기 완료</li>
              <li>농가 승인 대기</li>
            </ul>
          </div>
          <div>
            <h3>추천 근거</h3>
            <ul>
              <li>근거리 이동 가능</li>
              <li>작업 경험 적합</li>
              <li>일급 기준 충족</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
