export const research = [
  {
    authors: "Sousa CA, Zourdos MC, Storey AG, Helms ER",
    year: 2024,
    title:
      "The Importance of Recovery in Resistance Training Microcycle Construction",
    venue: "Journal of Human Kinetics 91:205–223",
    doi: "10.5114/jhk/186659",
    url: "https://pmc.ncbi.nlm.nih.gov/articles/PMC11057610/",
    feature: "Recovery and microcycle planning",
    limitations:
      "A narrative review. Recovery varies with the exercise, dose, training background, and outcome measured; it does not prescribe IronLog’s decay constants.",
    background: false,
  },
  {
    authors:
      "Morán-Navarro R, Pérez CE, Mora-Rodríguez R, de la Cruz-Sánchez E, González-Badillo JJ, Sánchez-Medina L, Pallarés JG",
    year: 2017,
    title:
      "Time course of recovery following resistance training leading or not to failure",
    venue: "European Journal of Applied Physiology 117:2387–2399",
    doi: "10.1007/s00421-017-3725-7",
    url: "https://pubmed.ncbi.nlm.nih.gov/28965198/",
    feature: "Effort-sensitive recovery estimates",
    limitations:
      "Ten trained men performing bench press and squat. Results cannot establish a universal recovery timetable for every person or muscle.",
    background: false,
  },
  {
    authors: "Varela-Olalla D, del Campo-Vecino J, Balsalobre-Fernández C",
    year: 2025,
    title:
      "Influence of Proximity to Failure, Relative Intensity, and Volume on Voluntary Performance and Fatigue Symptoms After Resistance Training: A Systematic Review",
    venue: "Journal of Strength and Conditioning Research 39:e1129–e1168",
    doi: "10.1519/JSC.0000000000005194",
    url: "https://pubmed.ncbi.nlm.nih.gov/40644670/",
    feature: "Volume and proximity-to-failure context",
    limitations:
      "A synthesis of heterogeneous protocols in healthy participants, not validation of a readiness score or a neurological fatigue detector.",
    background: false,
  },
  {
    authors: "Saw AE, Main LC, Gastin PB",
    year: 2016,
    title:
      "Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures: a systematic review",
    venue: "British Journal of Sports Medicine 50:281–291",
    doi: "10.1136/bjsports-2015-094758",
    url: "https://pubmed.ncbi.nlm.nih.gov/26423706/",
    feature: "Subjective sleep, soreness and energy check-ins",
    limitations:
      "Supports monitoring subjective responses. It does not validate IronLog’s numerical adjustment or check-in expiry window.",
    background: false,
  },
  {
    authors:
      "Bellenger CR, Fuller JT, Thomson RL, Davison K, Robertson EY, Buckley JD",
    year: 2016,
    title:
      "Monitoring Athletic Training Status Through Autonomic Heart Rate Regulation: A Systematic Review and Meta-Analysis",
    venue: "Sports Medicine 46:1461–1486",
    doi: "10.1007/s40279-016-0484-2",
    url: "https://link.springer.com/article/10.1007/s40279-016-0484-2",
    feature: "Background reading: autonomic monitoring",
    limitations:
      "Primarily endurance-training evidence. HRV integration is not implemented in IronLog Web.",
    background: true,
  },
  {
    authors: "Haddad M, Stylianides G, Djaoui L, Dellal A, Chamari K",
    year: 2017,
    title:
      "Session-RPE Method for Training Load Monitoring: Validity, Ecological Usefulness, and Influencing Factors",
    venue: "Frontiers in Neuroscience 11:612",
    doi: "10.3389/fnins.2017.00612",
    url: "https://www.frontiersin.org/journals/neuroscience/articles/10.3389/fnins.2017.00612/full",
    feature: "Background reading: session training load",
    limitations:
      "Whole-session perceived exertion differs from per-set RPE or RIR. Session-RPE load calculations are not implemented here.",
    background: true,
  },
];
export function Research() {
  return (
    <section id="research" className="research">
      <h2>The research behind the questions.</h2>
      <p>
        These papers inform design principles. They do{" "}
        <strong>not validate IronLog’s exact readiness score</strong>. The score
        and map are transparent training estimates—not diagnoses, measured
        muscle recovery, or a guarantee that you are ready to train.
      </p>
      {[false, true].map((background) => (
        <div key={String(background)}>
          <h3>
            {background
              ? "Background reading · not implemented integrations"
              : "Recovery & training methodology"}
          </h3>
          <ol>
            {research
              .filter((r) => r.background === background)
              .map((r) => (
                <li key={r.doi}>
                  <a href={r.url} target="_blank" rel="noreferrer">
                    {r.title}
                  </a>
                  <p>
                    {r.authors}. {r.year}. <i>{r.venue}.</i> DOI: {r.doi}
                  </p>
                  <small>
                    {r.feature}. {r.limitations}
                  </small>
                </li>
              ))}
          </ol>
        </div>
      ))}
    </section>
  );
}
