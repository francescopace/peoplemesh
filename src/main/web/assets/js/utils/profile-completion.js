const PROFILE_WEIGHTS = {
  identity: 10,
  roles: 15,
  skills: 15,
  industries: 10,
  personal: 10,
  interests: 10,
  geography: 10,
  consent: 10,
};

export function computeProfileScore(profile) {
  if (!profile) return 0;
  let score = 0;
  if (profile.identity?.display_name) score += PROFILE_WEIGHTS.identity;
  if (profile.professional?.roles?.length) score += PROFILE_WEIGHTS.roles;
  if (profile.professional?.skills_technical?.length) score += PROFILE_WEIGHTS.skills;
  if (profile.professional?.industries?.length) score += PROFILE_WEIGHTS.industries;
  const personal = profile.personal;
  if (personal && (personal.hobbies?.length || personal.sports?.length || personal.causes?.length || personal.personality_tags?.length)) {
    score += PROFILE_WEIGHTS.personal;
  }
  const interests = profile.interests_professional;
  if (interests && (interests.learning_areas?.length || interests.project_types?.length)) {
    score += PROFILE_WEIGHTS.interests;
  }
  if (profile.geography?.country) score += PROFILE_WEIGHTS.geography;
  if (profile.consent?.explicit) score += PROFILE_WEIGHTS.consent;
  return score;
}

export function listProfileCompletionHints(profile) {
  const missing = [];
  if (!profile?.professional?.roles?.length) missing.push("roles");
  if (!profile?.professional?.skills_technical?.length) missing.push("skills");
  if (!profile?.personal?.hobbies?.length && !profile?.personal?.sports?.length) missing.push("interests");
  if (!profile?.geography?.country) missing.push("location");
  return missing;
}
