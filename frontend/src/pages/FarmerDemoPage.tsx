export function FarmerDemoPage() {
  return (
    <div className="container">
      <div className="card">
        <h1>농가 요청 입력</h1>
        <div className="grid two-col">
          <div>
            <label>
              작업 요청
              <textarea rows={6} defaultValue="청도군 매전면에서 복숭아 적과 인력을 3명 구합니다. 11월 9일부터 11일까지 하루 12만 원, 식사 포함이며 장비 안전 점검이 필요합니다." />
            </label>
          </div>
          <div>
            <h3>AI 분석 결과</h3>
            <ul>
              <li>작업 유형: 복숭아 적과</li>
              <li>위치: 청도군 매전면</li>
              <li>기간: 2026-11-09 ~ 2026-11-11</li>
              <li>인원: 3명</li>
              <li>일급: 120,000원</li>
            </ul>
            <div className="row" style={{ marginTop: 16 }}>
              <button type="button">분석 요청</button>
              <button type="button" className="secondary">저장</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
